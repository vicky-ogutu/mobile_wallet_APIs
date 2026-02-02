package com.comulynx.wallet.rest.api.controller;

import java.util.List;
import java.util.Random;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.comulynx.wallet.rest.api.AppUtilities;
import com.comulynx.wallet.rest.api.model.Account;
import com.comulynx.wallet.rest.api.model.Customer;
import com.comulynx.wallet.rest.api.repository.AccountRepository;
import com.comulynx.wallet.rest.api.repository.CustomerRepository;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

	private static final Logger logger = LoggerFactory.getLogger(CustomerController.class);
	private Gson gson = new Gson();

	@Autowired
	private CustomerRepository customerRepository;
	@Autowired
	private AccountRepository accountRepository;

	@GetMapping("/")
	public List<Customer> getAllCustomers() {
		return customerRepository.findAll();
	}

	@PostMapping("/login")
	public ResponseEntity<?> customerLogin(@RequestBody String request) {

		try {
			JsonObject req = gson.fromJson(request, JsonObject.class);
			JsonObject response = new JsonObject();

			String customerId = req.get("customerId").getAsString();
			String pin = req.get("pin").getAsString();

			Customer customer = customerRepository.findByCustomerId(customerId)
					.orElseThrow(() -> new RuntimeException("Customer does not exist"));

			if (!customer.getPin().equals(pin)) {
				throw new RuntimeException("Invalid credentials");
			}

			Account account = accountRepository.findAccountByCustomerId(customerId)
					.orElseThrow(() -> new RuntimeException("Account not found"));

			response.addProperty("customerName",
					customer.getFirstName() + " " + customer.getLastName());
			response.addProperty("customerId", customer.getCustomerId());
			response.addProperty("email", customer.getEmail());
			response.addProperty("accountNo", account.getAccountNo());

			return ResponseEntity.ok(gson.toJson(response));

		} catch (Exception ex) {
			logger.error("Exception {}", AppUtilities.getExceptionStacktrace(ex));
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PostMapping("/")
	public ResponseEntity<?> createCustomer(@Valid @RequestBody Customer customer) {

		try {
			if (customerRepository.findByCustomerId(customer.getCustomerId()).isPresent()) {
				throw new RuntimeException("Customer with customerId exists");
			}

			if (customerRepository.findByEmail(customer.getEmail()).isPresent()) {
				throw new RuntimeException("Customer with email exists");
			}

			Customer savedCustomer = customerRepository.save(customer);

			Account account = new Account();
			account.setCustomerId(savedCustomer.getCustomerId());
			account.setAccountNo(generateAccountNo());
			account.setBalance(0.0);
			accountRepository.save(account);

			return ResponseEntity.ok(savedCustomer);

		} catch (Exception ex) {
			logger.error("Exception {}", AppUtilities.getExceptionStacktrace(ex));
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private String generateAccountNo() {
		return "AC" + (100000000 + new Random().nextInt(900000000));
	}
}
