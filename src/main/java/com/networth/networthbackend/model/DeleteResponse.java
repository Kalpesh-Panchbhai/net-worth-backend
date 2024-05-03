package com.networth.networthbackend.model;

import com.fasterxml.jackson.annotation.JsonKey;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class DeleteResponse {

    @JsonKey
    private String deletedKey;
}
