package com.countryservice.demo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.countryservice.demo.beans.Country;
import com.countryservice.demo.controllers.CountryController;
import com.countryservice.demo.services.CountryService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ControllerMockMvcTests {

	MockMvc mockMvc;

	@Mock
	CountryService countryService;

	@InjectMocks
	CountryController countryController;

	@BeforeEach
	public void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(countryController).build();
	}

	private String json(int id, String name, String capital) {
		return "{\"idCountry\":" + id + ",\"name\":\"" + name + "\",\"capital\":\"" + capital + "\"}";
	}

	@Test
	@Order(1)
	public void test_getAllCountries() throws Exception {
		List<Country> mycountries = new ArrayList<>();
		mycountries.add(new Country(1, "India", "Delhi"));
		mycountries.add(new Country(2, "USA", "Washington"));
		when(countryService.getAllCountries()).thenReturn(mycountries);

		mockMvc.perform(get("/getcountries"))
			.andExpect(status().isFound())
			.andDo(print());
	}

	@Test
	@Order(2)
	public void test_getCountryByID() throws Exception {
		when(countryService.getCountryById(1)).thenReturn(new Country(1, "India", "Delhi"));

		mockMvc.perform(get("/getcountries/{id}", 1))
			.andExpect(status().isFound())
			.andExpect(jsonPath("$.idCountry").value(1))
			.andExpect(jsonPath("$.name").value("India"))
			.andExpect(jsonPath("$.capital").value("Delhi"))
			.andDo(print());
	}

	@Test
	@Order(3)
	public void test_getCountryByName() throws Exception {
		when(countryService.getCountryByName("India")).thenReturn(new Country(1, "India", "Delhi"));

		mockMvc.perform(get("/getcountries/countryname").param("name", "India"))
			.andExpect(status().isFound())
			.andExpect(jsonPath("$.idCountry").value(1))
			.andExpect(jsonPath("$.name").value("India"))
			.andExpect(jsonPath("$.capital").value("Delhi"))
			.andDo(print());
	}

	@Test
	@Order(4)
	public void test_addCountry() throws Exception {
		when(countryService.addCountry(any(Country.class))).thenReturn(new Country(3, "Germany", "Berlin"));

		mockMvc.perform(post("/addcountry")
				.content(json(3, "Germany", "Berlin"))
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isCreated())
			.andDo(print());
	}

	@Test
	@Order(5)
	public void test_updateCountry() throws Exception {
		Country country = new Country(3, "Japan", "Tokyo");
		when(countryService.getCountryById(3)).thenReturn(country);
		when(countryService.updateCountry(any(Country.class))).thenReturn(country);

		mockMvc.perform(put("/updatecountry/{id}", 3)
				.content(json(3, "Japan", "Tokyo"))
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Japan"))
			.andExpect(jsonPath("$.capital").value("Tokyo"))
			.andDo(print());
	}

	@Test
	@Order(6)
	public void test_deleteCountry() throws Exception {
		when(countryService.getCountryById(3)).thenReturn(new Country(3, "Japan", "Tokyo"));

		mockMvc.perform(delete("/deletecountry/{id}", 3))
			.andExpect(status().isOk());
	}
}