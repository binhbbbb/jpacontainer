/*
 * JPAContainer
 * Copyright (C) 2010 Oy IT Mill Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.vaadin.addons.jpacontainer.demo;

import com.vaadin.addons.jpacontainer.demo.domain.Customer;
import com.vaadin.addons.jpacontainer.demo.domain.Order;
import com.vaadin.addons.jpacontainer.demo.domain.OrderItem;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A component that randomly generates test data for this demo application.
 * It implements the {@link ApplicationListener} interface and generates
 * the data when it receives a {@link ContextRefreshedEvent}.
 * <p>
 * When running inside a web application, this means that the data will be
 * generated once the application context has been fully initialized.
 * <p>
 * If you don't want the test data to be generated, either completely
 * delete this class or comment out the <code>@Repository</code>
 * annotation.
 *
 * @author Petter Holmström (IT Mill)
 * @since 1.0
 */
@Repository(value = "testDataGenerator")
public class TestDataGenerator implements
        ApplicationListener<ContextRefreshedEvent> {

    private final Log logger = LogFactory.getLog(getClass());
    @PersistenceContext
    private EntityManager entityManager;
    private ArrayList<Long> customerIds;

    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (logger.isInfoEnabled()) {
            logger.info("Received ContextRefreshedEvent, creating test data");
        }
        createTestData();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void createTestData() {
        if (entityManager == null) {
            throw new IllegalStateException("No EntityManager provided");
        }
        if (logger.isDebugEnabled()) {
            logger.debug(
                    "Generating test data for entity manager [" + entityManager + "]");
        }
        createCustomerTestData();
        createOrderTestData();
        entityManager.flush();
        if (logger.isDebugEnabled()) {
            logger.debug("Test data generation complete");
        }

        // Clean up
        customerIds.clear();
        customerIds = null;
    }
    final String[] salesReps = {"John Smith",
        "Scrooge McDuck",
        "Maxwell Smart",
        "Joe Cool",
        "Mick Dundee",
        "Adam Anderson",
        "Zandra Dickson"
    };
    final String[] products = {"Prepostulator",
        "Movable hole",
        "Chess computer",
        "Invisible ink",
        "Foobar",
        "Super glue",
        "Stargate",
        "Dial-home device",
        "Cellphone",
        "Kitchen sink",
        "Racecar",
        "Drumkit",
        "Submarine",
        "Some magazine",
        "Toilet paper",
        "Coal",
        "Orange juice"
    };
    final String[] fnames = {"Peter", "Alice", "Joshua", "Mike", "Olivia",
        "Nina", "Alex", "Rita", "Dan", "Umberto", "Henrik", "Rene",
        "Lisa", "Marge"};
    final String[] lnames = {"Smith", "Gordon", "Simpson", "Brown", "Clavel",
        "Simons", "Verne", "Scott", "Allison", "Gates", "Rowling",
        "Barks", "Ross", "Schneider", "Tate"};
    final String[] streets = {"Magna Avenue", "Fringilla Street",
        "Aliquet St.", "Pharetra Avenue", "Gravida St.", "Risus Street",
        "Ultricies Street", "Mi Avenue", "Libero Av.", "Purus Avenue"};
    final String[] postOffices = {"Stockholm", "Helsinki", "Paris",
        "London", "Luxemburg", "Duckburg", "New York", "Tokyo", "Athens",
        "Sydney"};
    final String[] countries = {"Sweden", "Finland", "France", "United Kingdom",
        "Luxemburg", "United States", "United States", "Japan", "Greece",
        "Australia"};

    private void createCustomerTestData() {
        if (logger.isDebugEnabled()) {
            logger.debug("Generating customers");
        }

        Random rnd = new Random();
        customerIds = new ArrayList(2000);
        for (int i = 0; i < 2000; i++) {
            Customer customer = new Customer();
            customer.setCustNo(i + 1);
            customer.setCustomerName(fnames[(int) (fnames.length * Math.random())] + " " + lnames[(int) (lnames.length * Math.
                    random())]);

            customer.getBillingAddress().setStreetOrBox(
                    (rnd.nextInt(1000) + 1) + " " + streets[(int) (streets.length * Math.
                    random())]);
            customer.getBillingAddress().setPostalCode(String.format("%05d", rnd.
                    nextInt(99998) + 1));
            int poIndex = (int) (postOffices.length * Math.random());
            customer.getBillingAddress().setPostOffice(postOffices[poIndex]);
            customer.getBillingAddress().setCountry(countries[poIndex]);

            customer.getShippingAddress().setStreetOrBox(
                    (rnd.nextInt(1000) + 1) + " " + streets[(int) (streets.length * Math.
                    random())]);
            customer.getShippingAddress().setPostalCode(String.format("%05d", rnd.
                    nextInt(99998) + 1));
            poIndex = (int) (postOffices.length * Math.random());
            customer.getShippingAddress().setPostOffice(postOffices[poIndex]);
            customer.getShippingAddress().setCountry(countries[poIndex]);
            customer.setNotes("No orders");
            entityManager.persist(customer);
            customerIds.add(customer.getId());
        }
    }

    private void createOrderTestData() {
        if (logger.isDebugEnabled()) {
            logger.debug("Generating orders");
        }

        Random rnd = new Random();
        for (int i = 0; i < 3000; i++) {
            Order order = new Order();
            Customer customer = entityManager.find(Customer.class, customerIds.
                    get(rnd.nextInt(customerIds.size())));
            order.setOrderNo(i + 1);
            order.setCustomer(customer);
            order.setOrderDate(createRandomDate());
            if (customer.getLastOrderDate() == null || customer.getLastOrderDate().
                    before(order.getOrderDate())) {
                customer.setLastOrderDate(order.getOrderDate());
            }
            customer.setNotes(""); // Removes the "No orders" default note.
            order.setCustomerReference(customer.getCustomerName());
            order.setSalesReference(
                    salesReps[(int) (salesReps.length * Math.random())]);

            order.getBillingAddress().setStreetOrBox(customer.getBillingAddress().getStreetOrBox());
            order.getBillingAddress().setPostalCode(customer.getBillingAddress().getPostalCode());
            order.getBillingAddress().setPostOffice(customer.getBillingAddress().getPostOffice());
            order.getBillingAddress().setCountry(customer.getBillingAddress().getCountry());

            order.getShippingAddress().setStreetOrBox(
                    (rnd.nextInt(1000) + 1) + " " + streets[(int) (streets.length * Math.
                    random())]);
            order.getShippingAddress().setPostalCode(String.format("%05d", rnd.
                    nextInt(99998) + 1));
            int poIndex = (int) (postOffices.length * Math.random());
            order.getShippingAddress().setPostOffice(postOffices[poIndex]);
            order.getShippingAddress().setCountry(countries[poIndex]);

            order.setShippedDate(addDaysToDate(order.getOrderDate(), rnd.nextInt(31)));

            for (int n = 0; n < rnd.nextInt(9) + 1; n++) {
                OrderItem item = new OrderItem();
                item.setDescription(products[(int) (products.length * Math.
                        random())]);
                item.setQuantity(rnd.nextInt(10) + 1);
                item.setPrice(rnd.nextInt(1000));
                order.addItem(item);
            }

            entityManager.persist(order);
        }
    }
    private static Random dateRnd = new Random();

    private static Date createRandomDate() {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, 1970 + dateRnd.nextInt(40));
        cal.set(Calendar.MONTH, dateRnd.nextInt(12));
        cal.set(Calendar.DATE,
                dateRnd.nextInt(cal.getMaximum(Calendar.DATE)) + 1);
        return cal.getTime();
    }

    private static Date addDaysToDate(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days);
        return cal.getTime();
    }
}