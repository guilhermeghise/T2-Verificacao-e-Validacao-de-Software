package com.shashirajraja.onlinebookstore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shashirajraja.onlinebookstore.dao.BookUserRepository;
import com.shashirajraja.onlinebookstore.dao.CustomerRepository;
import com.shashirajraja.onlinebookstore.dao.PurchaseDetailRepository;
import com.shashirajraja.onlinebookstore.dao.PurchaseHistoryRepository;
import com.shashirajraja.onlinebookstore.entity.Book;
import com.shashirajraja.onlinebookstore.entity.BookUser;
import com.shashirajraja.onlinebookstore.entity.Customer;
import com.shashirajraja.onlinebookstore.entity.PurchaseHistory;
import com.shashirajraja.onlinebookstore.entity.ShoppingCart;
import com.shashirajraja.onlinebookstore.entity.User;
import com.shashirajraja.onlinebookstore.util.TestDataFactory;

/**
 * Unit tests for the "checkout" business rules implemented in
 * {@link PaymentServiceImpl#createTransaction(Customer)} and for the
 * ownership rule in {@link PaymentServiceImpl#getPurchaseHistory(Customer, String)}.
 *
 * All collaborators (repositories) are mocked with Mockito so these tests
 * run in isolation, without a Spring context and without touching any
 * database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl - unit tests")
class PaymentServiceImplTest {

	@Mock
	private CustomerRepository customerRepos;

	@Mock
	private PurchaseHistoryRepository purchaseHistoryRepos;

	@Mock
	private PurchaseDetailRepository purchaseDetailRepos;

	@Mock
	private BookUserRepository bookUserRepos;

	@InjectMocks
	private PaymentServiceImpl paymentService;

	private Customer customer;

	@BeforeEach
	void setUp() {
		User user = TestDataFactory.aUser("alice", "{noop}encoded");
		customer = TestDataFactory.aCustomer("alice", user);
		customer.setShoppingCart(new HashSet<>());
		customer.setPurchaseHistories(new HashSet<>());
	}

	@Test
	@DisplayName("BR1: purchase succeeds and decreases stock when there is enough quantity available")
	void checkoutSucceedsAndDecreasesStock() {
		Book book = TestDataFactory.aBook("Clean Code", 5, 49.90);
		book.setId(1);
		customer.addShoppingCart(TestDataFactory.aCartItem(customer, book, 2));

		when(bookUserRepos.findById(any())).thenReturn(Optional.empty());
		when(bookUserRepos.addBookToUser(anyInt(), anyString())).thenReturn(1);

		String result = paymentService.createTransaction(customer);

		assertTrue(result.startsWith("T"), "a successful checkout must return a purchase history id, not an error message");
		assertEquals(3, book.getQuantity(), "stock must be decreased by the purchased quantity");
		assertTrue(customer.getShoppingCart().isEmpty(), "the shopping cart must be emptied after a successful purchase");
		assertEquals(1, customer.getPurchaseHistories().size(), "a purchase history record must be created");
		verify(customerRepos, times(1)).save(customer);
	}

	@Test
	@DisplayName("BR2: purchase is rejected when the requested quantity exceeds the available stock")
	void checkoutFailsWhenBookIsOutOfStock() {
		Book book = TestDataFactory.aBook("Refactoring", 1, 59.90);
		book.setId(2);
		customer.addShoppingCart(TestDataFactory.aCartItem(customer, book, 5));

		String result = paymentService.createTransaction(customer);

		assertEquals("Book named: Refactoring is out of stock!", result);
		assertEquals(1, book.getQuantity(), "stock must remain untouched when the purchase is rejected");
		assertFalse(customer.getShoppingCart().isEmpty(), "the cart must not be cleared when the purchase fails");
		verify(customerRepos, never()).save(any(Customer.class));
	}

	@Test
	@DisplayName("BR3: a book already linked to the customer is not linked again")
	void doesNotDuplicateExistingBookUserRelation() {
		Book book = TestDataFactory.aBook("Domain-Driven Design", 10, 89.90);
		book.setId(3);
		customer.addShoppingCart(TestDataFactory.aCartItem(customer, book, 1));

		when(bookUserRepos.findById(any())).thenReturn(Optional.of(new BookUser(book, customer)));

		paymentService.createTransaction(customer);

		verify(bookUserRepos, never()).addBookToUser(anyInt(), anyString());
	}

	@Test
	@DisplayName("BR4: a customer cannot retrieve another customer's purchase history")
	void cannotAccessAnotherCustomersPurchaseHistory() {
		Customer owner = TestDataFactory.aCustomer("bob", TestDataFactory.aUser("bob", "{noop}encoded"));
		PurchaseHistory history = new PurchaseHistory("T001", new java.util.Date());
		history.setCustomer(owner);

		when(purchaseHistoryRepos.findById("T001")).thenReturn(Optional.of(history));

		PurchaseHistory result = paymentService.getPurchaseHistory(customer, "T001");

		assertNull(result, "a purchase history that belongs to a different customer must not be returned");
	}

	@Test
	@DisplayName("BR4: the owning customer can retrieve their own purchase history")
	void ownerCanAccessTheirOwnPurchaseHistory() {
		PurchaseHistory history = new PurchaseHistory("T002", new java.util.Date());
		history.setCustomer(customer);

		when(purchaseHistoryRepos.findById("T002")).thenReturn(Optional.of(history));

		PurchaseHistory result = paymentService.getPurchaseHistory(customer, "T002");

		assertEquals(history, result);
	}

	@Test
	@DisplayName("Edge case: checking out with an empty cart still creates an (empty) purchase history")
	void checkoutWithEmptyCartCreatesEmptyPurchaseHistory() {
		// Documents current behaviour: the service does not validate that the
		// cart has at least one item before generating a transaction.
		String result = paymentService.createTransaction(customer);

		assertTrue(result.startsWith("T"));
		verify(customerRepos, times(1)).save(customer);
	}
}
