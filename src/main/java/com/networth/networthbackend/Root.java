package com.networth.networthbackend;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Root{
    public String id;
    public Double amount;
    public String credited_date;
    public String currency;
    public int income_tag;
    public int income_type;
    public Double tax_paid;
    public int user_id;
}