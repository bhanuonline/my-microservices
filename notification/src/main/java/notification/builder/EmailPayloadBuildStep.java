package notification.builder;

import lombok.RequiredArgsConstructor;
import notification.core.NotificationContext;
import notification.core.artifact.PersonalizationArtifact;
import notification.core.context.TemplateContext;
import notification.core.exception.NoEligibleRecipientsException;
import notification.core.experiment.TemplateVariantResolver;
import notification.core.template.Template;


import notification.core.template.TemplateProvider;
import notification.core.template.TemplateResolver;
import notification.integration.email.EmailRecipient;
import notification.integration.email.artifact.EmailPayloadArtifact;
import notification.integration.email.model.EmailPayload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * actual request building
 */
@Component
@RequiredArgsConstructor
public class EmailPayloadBuildStep implements BuildStep {

    //private final TemplateProvider templateProvider;
    private  final TemplateResolver templateResolver;
    private  final TemplateVariantResolver variantResolver;

    @Override
    public void execute(NotificationContext ctx) {

//        List<EmailPayload> payloads = ctx.getTarget().getEmails().stream()
//                .map(email -> EmailPayload.builder()
//                        .to(email)
//                        .subject("Welcome")
//                        .body(ctx.getMessage())
//                        .build()
//                ).toList();

//        List<EmailPayload> payloads =
//                ctx.getTarget().getRecipients().stream()
//                        .filter(EmailRecipient.class::isInstance)
//                        .map(EmailRecipient.class::cast)
//                        .map(recipient -> EmailPayload.builder()
//                                .recipient(recipient)
//                                .subject("Welcome")
//                                .body(ctx.getMessage())
//                                .build()
//                        )
//                        .toList();

        PersonalizationArtifact personalization =
                ctx.getArtifacts().get(PersonalizationArtifact.class);

        TemplateContext templateCtx =
                ctx.getArtifacts().getOrDefault(
                        TemplateContext.class,
                        TemplateContext::defaultContext
                );

//        List<EmailRecipient> recipients =
//                ctx.getTarget().getRecipients().stream()
//                        .filter(EmailRecipient.class::isInstance)
//                        .map(EmailRecipient.class::cast)
//                        .toList();

        List<EmailPayload> payloads =
                ctx.getTarget().getRecipients().stream()
                        .filter(EmailRecipient.class::isInstance)
                        .map(EmailRecipient.class::cast)
                        .map(recipient -> buildPayload(
                                ctx,
                                recipient,
                                personalization,
                                templateCtx
                        ))
                        .toList();

        //📌 Builder is responsible for validating its own channel
        //📌 Orchestrator decides what to do with the exception
        if (payloads.isEmpty()) {
            throw new NoEligibleRecipientsException(
                    "No email recipients found for notification"
            );
        }

//        List<EmailPayload> payloads =
//                recipients.stream()
//                        .map(recipient -> EmailPayload.builder()
//                                .recipient(recipient)
//                                .subject("Welcome")
//                                .body(ctx.getMessage())
//                                .build()
//                        )
//                        .toList();

        //ctx.getArtifacts().put(MetadataKeys.EMAIL_PAYLOADS, payloads);
        ctx.getArtifacts().put(
                new EmailPayloadArtifact(payloads)
        );
    }

    private String personalize(String template, Map<String, String> vars) {
        String result = template;
        for (var entry : vars.entrySet()) {
            result = result.replace(
                    "{{" + entry.getKey() + "}}",
                    entry.getValue()
            );
        }
        return result;
    }

    private EmailPayload buildPayload(
            NotificationContext ctx,
            EmailRecipient recipient,
            PersonalizationArtifact personalization,
            TemplateContext templateCtx) {

        Map<String, String> vars =
                personalization != null
                        ? personalization.variablesFor(recipient)
                        : Map.of();

        String variant =
                variantResolver.resolveVariant(recipient);

        Template template =
                templateResolver.resolve(
                        ctx.getType(),
                        recipient.getLanguage(),
                        templateCtx.getVersion(),
                        variant
                );

        String body =
                personalize(template.getBody(), vars);

        return EmailPayload.builder()
                .recipient(recipient)
                .subject("Welcome")
                .body(body)
                .build();
    }
}
