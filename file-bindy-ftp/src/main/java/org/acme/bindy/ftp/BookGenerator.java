/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.acme.bindy.ftp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Processor to generate {@Book}s with a random set of data.
 */
@ApplicationScoped
@Named
public class BookGenerator implements Processor {

    private static final String[] BOOK_GENRES = { "Action", "Crime", "Horror" };

    private static final String[] BOOK_DESCRIPTION = {
            "Awesome",
            "Amazing",
            "Fantastic",
            "Incredible",
            "Tremendous",
    };

    private static final String[] FIRST_NAMES = {
            "Fyodor",
            "Jane",
            "Leo",
            "Oscar",
            "William",
    };

    private static final String[] LAST_NAMES = {
            "Austen",
            "Dostoevsky",
            "Shakespeare",
            "Tolstoy",
            "Wilde",
    };

    @Override
    public void process(Exchange exchange) throws Exception {
        Random random = new Random();
        List<Book> books = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            String genre = BOOK_GENRES[random.nextInt(BOOK_GENRES.length)];
            String description = BOOK_DESCRIPTION[random.nextInt(BOOK_DESCRIPTION.length)];
            String title = String.format("The %s book of %s #%d", description, genre, i);

            String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            String author = String.format("%s %s", firstName, lastName);

            Book book = new Book();
            book.setId(i);
            book.setAuthor(author);
            book.setTitle(title);
            book.setGenre(genre);
            books.add(book);
        }

        exchange.getMessage().setBody(books);
    }
}
