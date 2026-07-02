package com.shashirajraja.onlinebookstore.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.shashirajraja.onlinebookstore.dao.BookRepository;
import com.shashirajraja.onlinebookstore.dao.BookUserRepository;
import com.shashirajraja.onlinebookstore.dao.CustomerRepository;
import com.shashirajraja.onlinebookstore.dao.UserRepository;
import com.shashirajraja.onlinebookstore.entity.Book;
import com.shashirajraja.onlinebookstore.entity.BookUserId;
import com.shashirajraja.onlinebookstore.entity.Customer;
import com.shashirajraja.onlinebookstore.entity.User;
import com.shashirajraja.onlinebookstore.service.PaymentService;
import com.shashirajraja.onlinebookstore.util.TestDataFactory;

/**
 * Integration tests: exercise the real {@link PaymentService} bean together
 * with the real Spring Data JPA repositories against an in-memory H2
 * database (see src/test/resources/application-test.properties).
 *
 * Unlike the unit tests, here we verify that the business rules are
 * correctly persisted: that the checkout journey actually survives a
 * round trip through Hibernate / the database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentServiceIntegrationTest {

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private BookUserRepository bookUserRepository;

	private Customer customer;
	private Book book;

	@BeforeEach
	void setUp() {
		User user = userRepository.save(TestDataFactory.aUser("carol", "{noop}secret"));
		customer = TestDataFactory.aCustomer("carol", user);
		customer.setShoppingCart(new HashSet<>());
		customer.setPurchaseHistories(new HashSet<>());
		customer = customerRepository.save(customer);

		book = bookRepository.save(TestDataFactory.aBook("Effective Java", 4, 79.90));
	}

	@Test
	@DisplayName("A successful checkout persists the stock decrease, the purchase history and the book-customer link")
	void checkoutPersistsAllExpectedChanges() {
		customer.addShoppingCart(TestDataFactory.aCartItem(customer, book, 3));

		String transactionId = paymentService.createTransaction(customer);

		assertTrue(transactionId.startsWith("T"));

		Book reloaded = bookRepository.findById(book.getId()).orElseThrow();
		assertEquals(1, reloaded.getQuantity(), "stock in the database must reflect the purchase");

		Customer reloadedCustomer = customerRepository.findById("carol").orElseThrow();
		assertTrue(reloadedCustomer.getShoppingCart().isEmpty(), "the cart must be empty in the database");
		assertEquals(1, reloadedCustomer.getPurchaseHistories().size());

		Optional<?> bookUser = bookUserRepository.findById(new BookUserId(reloaded, reloadedCustomer));
		assertTrue(bookUser.isPresent(), "the book must be linked to the customer after a successful purchase");
	}

	@Test
	@DisplayName("A rejected checkout (insufficient stock) does not change the stock in the database")
	void rejectedCheckoutDoesNotChangeStockInDatabase() {
		customer.addShoppingCart(TestDataFactory.aCartItem(customer, book, 999));

		String result = paymentService.createTransaction(customer);

		assertEquals("Book named: Effective Java is out of stock!", result);

		Book reloaded = bookRepository.findById(book.getId()).orElseThrow();
		assertEquals(4, reloaded.getQuantity(), "stock in the database must remain unchanged");
	}
}
