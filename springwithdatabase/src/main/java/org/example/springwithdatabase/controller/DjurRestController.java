package org.example.springwithdatabase.controller;

import org.example.springwithdatabase.dto.DjurDTO;
import org.example.springwithdatabase.repository.DjurRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/djur")
public class DjurRestController {

    private final DjurRepository djurRepository;

    public DjurRestController(DjurRepository djurRepository) {
        this.djurRepository = djurRepository;
    }

    @GetMapping
    public List<DjurDTO> getAllDjur() {
        return djurRepository.findAll().stream()
                .map(d -> new DjurDTO(d.getId(), d.getNamn(), d.getArt(), d.getAlder()))
                .toList();
    }
}
