package com.mustafatetik.atomcv.rendering.service;

import com.mustafatetik.atomcv.rendering.domain.SavedCustomization;
import com.mustafatetik.atomcv.rendering.repository.SavedCustomizations;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Named appearance settings, kept.
 *
 * <p><strong>A ceiling, because this is a list somebody scrolls.</strong>
 * Twenty is far more sets than anyone keeps and it is the difference between a
 * chooser and a table.
 */
@Service
public class CustomizationService {

    static final int MAX_PER_PROFILE = 20;

    private final SavedCustomizations customizations;

    CustomizationService(SavedCustomizations customizations) {
        this.customizations = customizations;
    }

    @Transactional(readOnly = true)
    public List<SavedCustomization> list(ProfileRef profile) {
        return customizations.findAll(profile);
    }

    /**
     * @throws ApiException {@code VALIDATION_FAILED} for an unknown template, a
     *                      name this profile already uses, or one set too many.
     *                      The unique index would refuse the duplicate anyway;
     *                      a constraint violation surfacing as a 500 tells the
     *                      caller the server broke (EK D.6.8's lesson)
     */
    @Transactional
    public SavedCustomization create(ProfileRef profile, String name,
            TemplateCustomization settings) {

        requireKnownTemplate(settings.baseTemplateId());
        if (customizations.findByName(profile, name.strip()).isPresent()) {
            throw invalid("name");
        }
        if (customizations.findAll(profile).size() >= MAX_PER_PROFILE) {
            throw invalid("name");
        }
        return customizations.save(profile, new SavedCustomization(
                profile.id(), name, settings,
                (short) TemplateRegistry.versionOf(settings.baseTemplateId())));
    }

    /**
     * Replaces the settings, and the name when one is sent.
     *
     * <p>Whole-object rather than field-by-field: Bolum 33.2's parameters are
     * read together by the renderer, and a half-applied geometry is a page
     * nobody asked for.
     */
    @Transactional
    public SavedCustomization update(ProfileRef profile, UUID id, String name,
            TemplateCustomization settings) {

        SavedCustomization saved = require(profile, id);
        if (settings != null) {
            requireKnownTemplate(settings.baseTemplateId());
            saved.apply(settings);
        }
        if (name != null && !name.isBlank()) {
            customizations.findByName(profile, name.strip())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw invalid("name");
                    });
            saved.rename(name);
        }
        return customizations.save(profile, saved);
    }

    @Transactional
    public void delete(ProfileRef profile, UUID id) {
        customizations.delete(profile, require(profile, id));
    }

    /**
     * What a generation should render with when it named one.
     *
     * <p>Empty for a null id, which is the ordinary case: Bolum 14.4's
     * {@code customizationId} is optional and a request that says nothing gets
     * the profile's own working settings.
     */
    @Transactional(readOnly = true)
    public Optional<TemplateCustomization> settingsOf(ProfileRef profile, UUID id) {
        return id == null
                ? Optional.empty()
                : Optional.of(require(profile, id).settings());
    }

    private SavedCustomization require(ProfileRef profile, UUID id) {
        return customizations.findById(profile, id)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static void requireKnownTemplate(String templateId) {
        if (!TemplateRegistry.ids().contains(templateId)) {
            throw invalid("baseTemplateId");
        }
    }

    private static ApiException invalid(String field) {
        return new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                .param("fields", List.of(field))
                .build());
    }
}
