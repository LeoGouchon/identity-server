package com.leogouchon.identityserver.email;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

@Component
public class EmailTemplateRenderer {
    private final SpringTemplateEngine templateEngine;

    public EmailTemplateRenderer(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String render(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(Map.copyOf(variables));
        return templateEngine.process(templateName, context);
    }
}
