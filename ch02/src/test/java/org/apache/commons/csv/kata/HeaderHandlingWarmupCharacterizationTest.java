/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.commons.csv.kata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.junit.jupiter.api.Test;

/**
 * Warm-up characterization tests for the "required headers" kata.
 *
 * <p>These tests do NOT implement the {@code setRequiredHeaders(String...)} feature described in the book's
 * chapter 2 requirement. They only pin down commons-csv's EXISTING header-handling behavior, so that a reader
 * stepping through them with a debugger builds an accurate mental model of where {@code CSVParser.createHeaders()}
 * and {@code CSVRecord.get(String)} currently stand, before adding any new production code.</p>
 */
class HeaderHandlingWarmupCharacterizationTest {

    @Test
    void headerlessCsv_accessesFieldsByIndex() throws IOException {
        final String csv = "2024-01-01,100.00,USD\n2024-01-02,200.00,EUR\n";
        try (CSVParser parser = CSVParser.builder()
                .setReader(new java.io.StringReader(csv))
                .setFormat(CSVFormat.DEFAULT)
                .get()) {

            final List<CSVRecord> records = parser.getRecords();

            assertEquals(2, records.size());
            assertEquals("2024-01-01", records.get(0).get(0));
            assertEquals("100.00", records.get(0).get(1));
            assertEquals("USD", records.get(0).get(2));
            // No header was configured at all, so there is no name-based mapping.
            assertEquals(null, parser.getHeaderMap());
        }
    }

    @Test
    void autoDetectedHeader_firstRowBecomesColumnNames() throws IOException {
        final String csv = "date,amount,currency\n2024-01-01,100.00,USD\n";
        final CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader() // empty varargs => auto-detect from the first physical row
                .get();
        try (CSVParser parser = CSVParser.builder()
                .setReader(new java.io.StringReader(csv))
                .setFormat(format)
                .get()) {

            final List<CSVRecord> records = parser.getRecords();

            assertEquals(Arrays.asList("date", "amount", "currency"), parser.getHeaderNames());
            assertEquals(1, records.size());
            assertEquals("USD", records.get(0).get("currency"));
        }
    }

    @Test
    void manuallySpecifiedHeader_withSkipHeaderRecordTrue_dataRowCountIsCorrect() throws IOException {
        final String csv = "date,amount,currency\n2024-01-01,100.00,USD\n";
        final CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("date", "amount", "currency")
                .setSkipHeaderRecord(true)
                .get();
        try (CSVParser parser = CSVParser.builder()
                .setReader(new java.io.StringReader(csv))
                .setFormat(format)
                .get()) {

            final List<CSVRecord> records = parser.getRecords();

            // Only the real data row is counted; the literal header line in the input was skipped.
            assertEquals(1, records.size());
            assertEquals("USD", records.get(0).get("currency"));
        }
    }

    @Test
    void manuallySpecifiedHeader_withoutSkipHeaderRecord_literalHeaderRowLeaksInAsData() throws IOException {
        // Real pitfall: manually specifying the header does NOT automatically consume the first
        // physical line. If that line also happens to look like a header, and setSkipHeaderRecord
        // is left at its default (false), it is parsed as an ordinary data row.
        final String csv = "date,amount,currency\n2024-01-01,100.00,USD\n";
        final CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("date", "amount", "currency")
                // setSkipHeaderRecord intentionally left at the default (false) to reproduce the pitfall.
                .get();
        try (CSVParser parser = CSVParser.builder()
                .setReader(new java.io.StringReader(csv))
                .setFormat(format)
                .get()) {

            final List<CSVRecord> records = parser.getRecords();

            // The literal header line "date,amount,currency" was NOT skipped, so it shows up as record #1.
            assertEquals(2, records.size());
            assertEquals("date", records.get(0).get("date"));
            assertEquals("2024-01-01", records.get(1).get("date"));
        }
    }

    @Test
    void duplicateHeaderNames_defaultFormatAllowsThemByDefault() throws IOException {
        // CSVFormat.Builder.create() sets DuplicateHeaderMode.ALLOW_ALL as the Java-level default
        // (see CSVFormat.java Builder.create(), NOT CSVFormat.validate()'s own field default).
        final String csv = "a,a,b\n1,2,3\n";
        final CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader() // auto-detect
                .get();
        try (CSVParser parser = CSVParser.builder()
                .setReader(new java.io.StringReader(csv))
                .setFormat(format)
                .get()) {

            final List<CSVRecord> records = parser.getRecords();

            // Both "a" columns are allowed; the header map keeps only the LAST occurrence's index.
            assertEquals(1, records.size());
            assertEquals(DuplicateHeaderMode.ALLOW_ALL, format.getDuplicateHeaderMode());
            assertEquals(Integer.valueOf(1), parser.getHeaderMap().get("a"));
        }
    }

    @Test
    void duplicateHeaderNames_disallowModeThrowsBeforeFirstRecord() {
        final String csv = "a,a,b\n1,2,3\n";
        final CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader() // auto-detect
                .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
                .get();

        final IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> CSVParser.builder().setReader(new java.io.StringReader(csv)).setFormat(format).get());

        assertTrue(thrown.getMessage().contains("duplicate name"));
    }

    @Test
    void emptyHeaderName_disallowedByDefault_throwsBeforeFirstRecord() {
        final String csv = "date,,currency\n2024-01-01,100.00,USD\n";
        final CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader() // auto-detect; allowMissingColumnNames defaults to false
                .get();

        final IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> CSVParser.builder().setReader(new java.io.StringReader(csv)).setFormat(format).get());

        assertTrue(thrown.getMessage().contains("header name is missing"));
    }

    @Test
    void emptyHeaderName_allowedWhenAllowMissingColumnNamesTrue() throws IOException {
        final String csv = "date,,currency\n2024-01-01,100.00,USD\n";
        final CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader() // auto-detect
                .setAllowMissingColumnNames(true)
                .get();
        try (CSVParser parser = CSVParser.builder()
                .setReader(new java.io.StringReader(csv))
                .setFormat(format)
                .get()) {

            final List<CSVRecord> records = parser.getRecords();

            assertEquals(1, records.size());
            // Real pitfall: an allowed blank column name is NOT dropped — it becomes a literal ""
            // key in both getHeaderMap() and getHeaderNames(), sitting right next to the real columns.
            assertTrue(parser.getHeaderNames().contains(""));
            assertEquals("USD", records.get(0).get("currency"));
        }
    }

    @Test
    void missingRequiredColumn_todayOnlyFailsLazilyWhenGetIsCalled() throws IOException {
        // This is the baseline behavior the book's "required headers" feature is meant to improve on:
        // nothing fails while the header is being built, even though "currency" never appears in it.
        final String csv = "date,amount\n2024-01-01,100.00\n";
        final CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader() // auto-detect: header map ends up as {date=0, amount=1}
                .get();
        try (CSVParser parser = CSVParser.builder()
                .setReader(new java.io.StringReader(csv))
                .setFormat(format)
                .get()) {

            // Constructing the parser (and therefore building the header map) succeeds today,
            // even though a hypothetical "currency" requirement is already unmet at this point.
            final List<CSVRecord> records = parser.getRecords();
            final CSVRecord firstRecord = records.get(0);

            final IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> firstRecord.get("currency"));
            assertEquals("Mapping for currency not found, expected one of [date, amount]", thrown.getMessage());
        }
    }
}
