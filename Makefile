# Recipes are POSIX shell. On Windows run this from Git Bash: when sh.exe is
# not on PATH, GNU Make falls back to cmd.exe and every recipe fails with
# "'.' is not recognized", which says nothing about the real cause.
ifeq ($(SHELL),sh.exe)
$(error Run make from Git Bash. cmd.exe and PowerShell cannot execute these recipes)
endif

# Docker Compose reads .env by itself, Spring does not. Without this the app
# falls back to the default password in application-local.yml while the
# database container was created with whatever .env holds, and every startup
# dies on "password authentication failed".
ifneq (,$(wildcard .env))
include .env
export
endif

# Invoked through sh on purpose. GNU Make on Windows runs a recipe line that
# holds no shell metacharacters directly through CreateProcess, and ./gradlew
# is not a Windows executable, so a bare ./gradlew bypasses the shell and
# fails. Naming sh keeps one spelling that works on Windows and on Linux.
GRADLE := sh ./gradlew

.PHONY: dev dev-full db-reset record test test-int golden-costs

## core services (postgres, redis, mailpit) + backend with the fake LLM
dev:
	docker compose --profile core up -d
	$(GRADLE) bootRun --args='--spring.profiles.active=local,local-fake'

## also starts the heavier containers (latex, embeddings) once they exist
dev-full:
	docker compose --profile core --profile full up -d

## wipe the database volume; LOCAL ONLY
db-reset:
	docker compose --profile core down -v
	docker compose --profile core up -d postgres
	@echo "Database wiped. Flyway applies the migrations on the next 'make dev'."

## real LLM calls, responses saved as fixtures
record:
	$(GRADLE) bootRun --args='--spring.profiles.active=local,local-record'

## unit + architecture tests
test:
	$(GRADLE) test

## integration tests (Testcontainers, needs Docker)
test-int:
	$(GRADLE) integrationTest

## re-measure the golden set's render costs (needs Docker; builds the LaTeX image)
golden-costs:
	$(GRADLE) latexTest --tests '*GoldenCostsIT' -Dgolden.record=true
