package com.supermarket.pos.auth.controller;

import com.supermarket.pos.auth.dto.AuthDtos.ApprovalGrantDto;
import com.supermarket.pos.auth.dto.AuthDtos.ApprovalRequest;
import com.supermarket.pos.auth.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Approvals", description = "Manager approvals entered on the cashier's screen")
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @Operation(summary = "A manager approves an action for the signed-in user; returns a single-use token (2 min)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalGrantDto grant(@Valid @RequestBody ApprovalRequest request) {
        return approvals.grant(request);
    }
}
