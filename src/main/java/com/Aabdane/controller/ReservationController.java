package com.Aabdane.controller;

import com.Aabdane.ReservationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tickets")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/reserve/{id}")
    public String reserveTicket(@PathVariable Long id) throws InterruptedException {
        return reservationService.reserveTicket(id);
    }
}