package com.example.apigateway.adminui;

import com.example.apigateway.security.AdminAuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.reactive.result.view.RedirectView;

/**
 * Active when gateway.admin.ui=react (default). GET /admin/ui → 302 redirect to
 * the external React app configured in gateway.admin.react-url.
 *
 * Existence gated by @ConditionalOnProperty so switching ui=thymeleaf drops this
 * bean and loads AdminUiThymeleafController instead.
 */
@Controller
@RequestMapping("/admin/ui")
@ConditionalOnProperty(prefix = "gateway.admin", name = "ui", havingValue = "REACT", matchIfMissing = true)
public class AdminUiRedirectController {

    private final AdminAuthProperties props;

    public AdminUiRedirectController(AdminAuthProperties props) {
        this.props = props;
    }

    @GetMapping({"", "/", "/**"})
    public RedirectView toReact() {
        RedirectView view = new RedirectView(props.getReactUrl());
        view.setStatusCode(HttpStatus.FOUND);
        return view;
    }
}
