// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.example.controller;

import com.example.model.DataItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SampleController {

    private final Map<String, DataItem> dataStore = new HashMap<>();

    @GetMapping("/data/{id}")
    public ResponseEntity<DataItem> getData(@PathVariable String id) {
        DataItem item = dataStore.get(id);
        if (item != null) {
            return ResponseEntity.ok(item);
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/data/{id}")
    public ResponseEntity<DataItem> putData(@PathVariable String id, @RequestBody DataItem item) {
        item.setId(id);
        dataStore.put(id, item);
        return ResponseEntity.ok(item);
    }
}
