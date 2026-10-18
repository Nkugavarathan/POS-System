package com.supermarket.pos.pos.controller;

import com.supermarket.pos.common.web.PageResponse;
import com.supermarket.pos.pos.dto.PosDtos.CashMovementDto;
import com.supermarket.pos.pos.dto.PosDtos.CashMovementRequest;
import com.supermarket.pos.pos.dto.PosDtos.CloseShiftRequest;
import com.supermarket.pos.pos.dto.PosDtos.HeldCartDto;
import com.supermarket.pos.pos.dto.PosDtos.HoldCartRequest;
import com.supermarket.pos.pos.dto.PosDtos.OpenShiftRequest;
import com.supermarket.pos.pos.dto.PosDtos.ShiftDto;
import com.supermarket.pos.pos.dto.PosDtos.ShiftSummaryDto;
import com.supermarket.pos.pos.dto.PosDtos.VoidDto;
import com.supermarket.pos.pos.dto.PosDtos.VoidRequest;
import com.supermarket.pos.pos.entity.CashierShift;
import com.supermarket.pos.pos.service.CounterService;
import com.supermarket.pos.pos.service.ShiftService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Tag(name = "POS", description = "Shifts, cash drawer, held carts and voids")
@RestController
@RequestMapping("/api/v1/pos")
public class PosController {

    private final ShiftService shifts;
    private final CounterService counter;

    public PosController(ShiftService shifts, CounterService counter) {
        this.shifts = shifts;
        this.counter = counter;
    }

    @GetMapping("/shifts/current")
    public ResponseEntity<ShiftDto> current() {
        return shifts.current().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftDto open(@Valid @RequestBody OpenShiftRequest request) {
        return shifts.open(request.registerId(), request.openingFloat());
    }

    @GetMapping("/shifts")
    public PageResponse<ShiftDto> list(@RequestParam(required = false) Long cashierId,
                                       @RequestParam(required = false) Long registerId,
                                       @RequestParam(required = false) CashierShift.Status status,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size) {
        return shifts.list(cashierId, registerId, status, from, to, page, size);
    }

    @GetMapping("/shifts/{id}/summary")
    public ShiftSummaryDto summary(@PathVariable Long id) {
        return shifts.summary(id);
    }

    @PostMapping("/shifts/{id}/cash-movements")
    public ResponseEntity<CashMovementDto> cashMovement(@PathVariable Long id,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                        @Valid @RequestBody CashMovementRequest request) {
        var outcome = shifts.cashMovement(id, request, key);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(outcome.value());
    }

    @PostMapping("/shifts/{id}/close")
    public ShiftSummaryDto close(@PathVariable Long id,
                                 @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                 @Valid @RequestBody CloseShiftRequest request) {
        return shifts.close(id, request, key).value();
    }

    @PostMapping("/held-carts")
    @ResponseStatus(HttpStatus.CREATED)
    public HeldCartDto hold(@Valid @RequestBody HoldCartRequest request) {
        return counter.hold(request);
    }

    @GetMapping("/held-carts")
    public List<HeldCartDto> held() {
        return counter.held();
    }

    @PostMapping("/held-carts/{id}/resume")
    public HeldCartDto resume(@PathVariable Long id) {
        return counter.resume(id);
    }

    @PostMapping("/held-carts/{id}/cancel")
    public HeldCartDto cancelHeld(@PathVariable Long id) {
        return counter.cancel(id);
    }

    @PostMapping("/voids")
    @ResponseStatus(HttpStatus.CREATED)
    public VoidDto recordVoid(@Valid @RequestBody VoidRequest request) {
        return counter.recordVoid(request);
    }
}
