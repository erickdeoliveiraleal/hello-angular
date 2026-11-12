package com.example.backend;

import java.util.Locale;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UppercaseController {

	public record NameRequest(String name) {
	}

	public record NameResponse(String result) {
	}

	@PostMapping("/protegido/uppercase")
	public NameResponse uppercase(@RequestBody NameRequest request) {
		String name = request.name() == null ? "" : request.name();
		return new NameResponse(name.toUpperCase(Locale.ROOT));
	}

}
