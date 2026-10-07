package com.example.backoffice.controller;

import com.example.backoffice.client.GatewayClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Thymeleaf wrappers over the existing REST admin endpoints.
 *   /sagas  → /admin/sagas
 *   /dlq    → /admin/dlq
 *   /flags  → /admin/flags
 */
@Controller
public class OperationsController {

    private final GatewayClient gateway;
    private final ObjectMapper mapper = new ObjectMapper();

    public OperationsController(GatewayClient gateway) { this.gateway = gateway; }

    // ─── sagas ───
    @GetMapping("/sagas")
    public String sagas(@RequestParam(required = false) String state, Model model) {
        model.addAttribute("activeNav", "sagas");
        model.addAttribute("state", state);
        model.addAttribute("sagas", gateway.listSagas(state));
        return "sagas/list";
    }

    @GetMapping("/sagas/{id}")
    public String sagaDetail(@PathVariable String id, Model model) {
        model.addAttribute("activeNav", "sagas");
        model.addAttribute("id", id);
        model.addAttribute("detail", gateway.sagaDetail(id));
        return "sagas/detail";
    }

    @PostMapping("/sagas/{id}/compensate")
    public String compensate(@PathVariable String id,
                             @RequestParam(defaultValue = "operator-triggered") String reason) {
        gateway.compensateSaga(id, reason);
        return "redirect:/sagas/" + id;
    }

    // ─── DLQ ───
    @GetMapping("/dlq")
    public String dlq(@RequestParam(required = false) String status, Model model) {
        model.addAttribute("activeNav", "dlq");
        model.addAttribute("status", status);
        model.addAttribute("entries", gateway.listDlq(status));
        return "dlq/list";
    }

    @GetMapping("/dlq/{id}")
    public String dlqDetail(@PathVariable Long id, Model model) {
        model.addAttribute("activeNav", "dlq");
        model.addAttribute("id", id);
        model.addAttribute("entry", gateway.dlqDetail(id));
        return "dlq/detail";
    }

    @PostMapping("/dlq/{id}/replay")
    public String dlqReplay(@PathVariable Long id) {
        gateway.replayDlq(id);
        return "redirect:/dlq";
    }

    @PostMapping("/dlq/{id}/ack")
    public String dlqAck(@PathVariable Long id) {
        gateway.ackDlq(id);
        return "redirect:/dlq";
    }

    // ─── Feature flags ───
    @GetMapping("/flags")
    public String flags(Model model) {
        model.addAttribute("activeNav", "flags");
        model.addAttribute("flags", gateway.listFlags());
        return "flags/list";
    }

    @PostMapping("/flags/{key}/toggle")
    public String toggleFlag(@PathVariable String key) {
        gateway.toggleFlag(key);
        return "redirect:/flags";
    }

    @PostMapping("/flags/{key}")
    public String upsertFlag(@PathVariable String key,
                             @RequestParam(required = false) String description,
                             @RequestParam(defaultValue = "false") boolean enabled,
                             @RequestParam(defaultValue = "0") int percentage,
                             @RequestParam(required = false) String tenantWhitelist,
                             @RequestParam(required = false) String userWhitelist) {
        ObjectNode body = mapper.createObjectNode();
        if (description != null) body.put("description", description);
        body.put("enabled", enabled);

        ObjectNode rules = mapper.createObjectNode().put("percentage", Math.max(0, Math.min(100, percentage)));
        rules.set("tenantWhitelist", csvToArray(tenantWhitelist));
        rules.set("userWhitelist",   csvToArray(userWhitelist));
        body.put("rulesJson", rules.toString());

        gateway.upsertFlag(key, body);
        return "redirect:/flags";
    }

    private com.fasterxml.jackson.databind.node.ArrayNode csvToArray(String csv) {
        var arr = mapper.createArrayNode();
        if (csv == null || csv.isBlank()) return arr;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) arr.add(t);
        }
        return arr;
    }

    /** Expose JSON to the templates in a null-safe way (silences Thymeleaf NPE on null). */
    public JsonNode safe(JsonNode n) { return n == null ? mapper.createObjectNode() : n; }
}
