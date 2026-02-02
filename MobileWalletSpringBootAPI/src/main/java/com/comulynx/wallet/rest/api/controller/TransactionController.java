package com.comulynx.wallet.rest.api.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.comulynx.wallet.rest.api.AppUtilities;
import com.comulynx.wallet.rest.api.exception.ResourceNotFoundException;
import com.comulynx.wallet.rest.api.model.Account;
import com.comulynx.wallet.rest.api.model.Transaction;
import com.comulynx.wallet.rest.api.repository.AccountRepository;
import com.comulynx.wallet.rest.api.repository.TransactionRepository;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

	private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);
	private final Gson gson = new Gson();

	@Autowired
	private TransactionRepository transactionRepository;

	@Autowired
	private AccountRepository accountRepository;

	// -------------------------------------------------------------------------
	// Get all transactions
	// -------------------------------------------------------------------------
	@GetMapping("/")
	public List<Transaction> getAllTransaction() {
		return transactionRepository.findAll();
	}

	// -------------------------------------------------------------------------
	// Last 100 transactions by customerId
	// -------------------------------------------------------------------------
	@PostMapping(
			value = "/last-100-transactions",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE
	)
	public ResponseEntity<?> getLast100TransactionsByCustomerId(@RequestBody String request)
			throws ResourceNotFoundException {

		try {
			JsonObject req = gson.fromJson(request, JsonObject.class);
			String customerId = req.get("customerId").getAsString();

			List<Transaction> transactions = transactionRepository
					.findTransactionsByCustomerId(customerId)
					.orElseThrow(() -> new ResourceNotFoundException("No transactions found"));

			List<Transaction> last100 = transactions.stream()
					.sorted(Comparator.comparingLong(Transaction::getId).reversed())
					.limit(100)
					.collect(Collectors.toList());

			return ResponseEntity.ok(gson.toJson(last100));

		} catch (Exception ex) {
			logger.error("Exception {}", AppUtilities.getExceptionStacktrace(ex));
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	// -------------------------------------------------------------------------
	// Send money
	// -------------------------------------------------------------------------
	@PostMapping(
			value = "/send-money",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE
	)
	public ResponseEntity<?> doSendMoneyTransaction(@RequestBody String request)
			throws ResourceNotFoundException {

		try {
			Random rand = new Random();
			JsonObject req = gson.fromJson(request, JsonObject.class);
			JsonObject response = new JsonObject();

			String customerId = req.get("customerId").getAsString();
			String accountFrom = req.get("accountFrom").getAsString();
			String accountTo = req.get("accountTo").getAsString();
			double amount = req.get("amount").getAsDouble();

			Account sender = accountRepository.findAccountByAccountNo(accountFrom)
					.orElseThrow(() -> new ResourceNotFoundException("Sender account not found"));

			Account receiver = accountRepository.findAccountByAccountNo(accountTo)
					.orElseThrow(() -> new ResourceNotFoundException("Receiver account not found"));

			if (sender.getBalance() < amount) {
				throw new RuntimeException("Insufficient balance");
			}

			// Update balances
			sender.setBalance(sender.getBalance() - amount);
			receiver.setBalance(receiver.getBalance() + amount);
			accountRepository.save(sender);
			accountRepository.save(receiver);

			// Debit transaction
			Transaction debit = new Transaction();
			debit.setTransactionId("TRN" + rand.nextInt(100000));
			debit.setCustomerId(customerId);
			debit.setAccountNo(accountFrom);
			debit.setAmount(amount);
			debit.setBalance(sender.getBalance());
			debit.setTransactionType("FT");
			debit.setDebitOrCredit("Debit");
			transactionRepository.save(debit);

			// Credit transaction
			Transaction credit = new Transaction();
			credit.setTransactionId("TRN" + rand.nextInt(100000));
			credit.setCustomerId(receiver.getCustomerId());
			credit.setAccountNo(accountTo);
			credit.setAmount(amount);
			credit.setBalance(receiver.getBalance());
			credit.setTransactionType("FT");
			credit.setDebitOrCredit("Credit");
			transactionRepository.save(credit);

			response.addProperty("response_status", true);
			response.addProperty("response_message", "Transaction Successful");

			return ResponseEntity.ok(gson.toJson(response));

		} catch (Exception ex) {
			logger.error("Exception {}", AppUtilities.getExceptionStacktrace(ex));
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	// -------------------------------------------------------------------------
	// Mini statement (last 5 transactions)
	// -------------------------------------------------------------------------
	@PostMapping(
			value = "/mini-statement",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE
	)
	public ResponseEntity<?> getMiniStatementByCustomerIdAndAccountNo(@RequestBody String request)
			throws ResourceNotFoundException {

		try {
			JsonObject req = gson.fromJson(request, JsonObject.class);
			String customerId = req.get("customerId").getAsString();
			String accountNo = req.get("accountNo").getAsString();

			// Pageable: first page, 5 records, latest first
			Pageable pageable = PageRequest.of(
					0,
					5,
					Sort.by("id").descending()
			);

			List<Transaction> transactions =
					transactionRepository.getMiniStatementUsingCustomerIdAndAccountNo(
							customerId,
							accountNo,
							pageable
					);

			return ResponseEntity.ok(gson.toJson(transactions));

		} catch (Exception ex) {
			logger.error("Exception {}", AppUtilities.getExceptionStacktrace(ex));
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}

