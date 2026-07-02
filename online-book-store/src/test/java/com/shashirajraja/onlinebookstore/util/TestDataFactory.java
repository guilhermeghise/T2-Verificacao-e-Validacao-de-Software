package com.shashirajraja.onlinebookstore.util;

import com.shashirajraja.onlinebookstore.entity.Book;
import com.shashirajraja.onlinebookstore.entity.BookDetail;
import com.shashirajraja.onlinebookstore.entity.Customer;
import com.shashirajraja.onlinebookstore.entity.ShoppingCart;
import com.shashirajraja.onlinebookstore.entity.User;

/**
 * Centralizes the creation of valid domain objects used across the
 * integration and system tests, so every test starts from the same,
 * well known baseline and only changes the one attribute that is
 * relevant for the scenario under test.
 */
public final class TestDataFactory {

	private TestDataFactory() {
	}

	public static BookDetail aBookDetail() {
		return new BookDetail("Fiction", "A great book about testing", 0);
	}

	public static Book aBook(String name, int quantity, double price) {
		return new Book(name, quantity, price, aBookDetail());
	}

	public static User aUser(String username, String encodedPassword) {
		return new User(username, encodedPassword, true);
	}

	public static Customer aCustomer(String username, User user) {
		Customer customer = new Customer(username, "Jane", "Doe", username + "@example.com", 11999999999L,
				"123 Main St");
		customer.setUser(user);
		return customer;
	}

	public static ShoppingCart aCartItem(Customer customer, Book book, int quantity) {
		return new ShoppingCart(customer, book, quantity);
	}
}
