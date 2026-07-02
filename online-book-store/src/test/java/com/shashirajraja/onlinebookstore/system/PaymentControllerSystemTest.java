package com.shashirajraja.onlinebookstore.system;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.shashirajraja.onlinebookstore.dao.BookRepository;
import com.shashirajraja.onlinebookstore.dao.CustomerRepository;
import com.shashirajraja.onlinebookstore.dao.UserRepository;
import com.shashirajraja.onlinebookstore.entity.Book;
import com.shashirajraja.onlinebookstore.entity.Customer;
import com.shashirajraja.onlinebookstore.entity.User;
import com.shashirajraja.onlinebookstore.util.TestDataFactory;

/**
 * System / end-to-end tests for the "finalizar compra" (checkout) user
 * journey. These tests go through the real HTTP layer (MockMvc against the
 * real DispatcherServlet), the real Spring Security filter chain, the real
 * PaymentController, the real PaymentServiceImpl and a real (in-memory H2)
 * database - i.e. every layer of the application working together, the
 * same way a browser request would traverse the system.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentControllerSystemTest {

	private static final String USERNAME = "dave";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private BookRepository bookRepository;

	@BeforeEach
	void setUp() {
		User user = userRepository.save(TestDataFactory.aUser(USERNAME, "{noop}secret"));
		Customer customer = TestDataFactory.aCustomer(USERNAME, user);
		customer.setShoppingCart(new HashSet<>());
		customer.setPurchaseHistories(new HashSet<>());
		customerRepository.save(customer);
	}

	@Test
	@WithMockUser(username = USERNAME, roles = "CUSTOMER")
	@DisplayName("System: a customer completes checkout for a book that is in stock")
	void customerCompletesCheckoutSuccessfully() throws Exception {
		Book book = bookRepository.save(TestDataFactory.aBook("The Pragmatic Programmer", 2, 65.0));
		Customer customer = customerRepository.findById(USERNAME).orElseThrow();
		customer.addShoppingCart(TestDataFactory.aCartItem(customer, book, 1));
		customerRepository.save(customer);

		mockMvc.perform(post("/customers/payment/success")
					.with(csrf())
					.param("upi", "dave@upi")
					.param("otp", "123456"))
				.andExpect(status().isOk())
				.andExpect(view().name("customer-transaction-detail"))
				.andExpect(model().attribute("message", containsString("Payment Successful")));

		Book reloaded = bookRepository.findById(book.getId()).orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals(1, reloaded.getQuantity());
	}

	@Test
	@WithMockUser(username = USERNAME, roles = "CUSTOMER")
	@DisplayName("System: checkout is rejected end-to-end when the book is out of stock")
	void customerCheckoutIsRejectedWhenOutOfStock() throws Exception {
		Book book = bookRepository.save(TestDataFactory.aBook("Working Effectively with Legacy Code", 1, 70.0));
		Customer customer = customerRepository.findById(USERNAME).orElseThrow();
		customer.addShoppingCart(TestDataFactory.aCartItem(customer, book, 5));
		customerRepository.save(customer);

		mockMvc.perform(post("/customers/payment/success")
					.with(csrf())
					.param("upi", "dave@upi")
					.param("otp", "123456"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("message", containsString("is out of stock")));

		Book reloaded = bookRepository.findById(book.getId()).orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals(1, reloaded.getQuantity(), "stock must be unchanged end-to-end");
	}

	@Test
	@DisplayName("System: an unauthenticated user is redirected to the login page instead of reaching checkout")
	void unauthenticatedUserCannotReachCheckout() throws Exception {
		mockMvc.perform(get("/customers/cart/pay"))
				.andExpect(status().is3xxRedirection());
	}
}
