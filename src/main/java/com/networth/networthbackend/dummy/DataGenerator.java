package com.networth.networthbackend.dummy;


import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.WatchEntity;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class DataGenerator {

    public final List<AccountEntity> accountList = new ArrayList<>();
    public final List<AccountBrokerEntity> accountBrokerEntityList = new ArrayList<>();
    public final List<WatchEntity> watchList = new ArrayList<>();

    public DataGenerator() {
//        generateAccountData();
//        generateAccountBrokerData();
//        generateWatchData();
    }

    private void generateAccountData() {
        AccountEntity account1 = new AccountEntity("account1", "user1", "Saving1", "Saving", "", "INR", true);
        AccountEntity account2 = new AccountEntity("account2", "user1", "Saving2", "Saving", "", "INR", true);
        AccountEntity account3 = new AccountEntity("account3", "user1", "Credit Card1", "Credit Card", "", "INR", true);
        AccountEntity account4 = new AccountEntity("account4", "user1", "Credit Card2", "Credit Card", "", "INR", true);
        AccountEntity account5 = new AccountEntity("account5", "user1", "Broker1", "Broker", "", "INR", true);
        AccountEntity account6 = new AccountEntity("account6", "user1", "Broker2", "Broker", "", "INR", true);
        accountList.add(account1);
        accountList.add(account2);
        accountList.add(account3);
        accountList.add(account4);
        accountList.add(account5);
        accountList.add(account6);
    }

    private void generateAccountBrokerData() {
        AccountBrokerEntity accountBroker1 = new AccountBrokerEntity("accountBroker1", "account5", "NOW", "", 0.0);
        AccountBrokerEntity accountBroker2 = new AccountBrokerEntity("accountBroker2", "account5", "AAPL", "", 0.0);
        accountBrokerEntityList.add(accountBroker1);
        accountBrokerEntityList.add(accountBroker2);


        AccountBrokerEntity accountBroker3 = new AccountBrokerEntity("accountBroker3", "account6", "0P00011MAX.BO", "", 0.0);
        AccountBrokerEntity accountBroker4 = new AccountBrokerEntity("accountBroker4", "account6", "AMZN", "", 0.0);
        accountBrokerEntityList.add(accountBroker3);
        accountBrokerEntityList.add(accountBroker4);
    }

    private void generateWatchData() {
        WatchEntity watch1 = new WatchEntity("watch1", "user1", "All", Arrays.asList("account1", "account2", "account3", "account4", "account5", "account6"));
        watchList.add(watch1);

        WatchEntity watch2 = new WatchEntity("watch2", "user1", "watch1", Arrays.asList("account1", "account3", "account5"));
        watchList.add(watch2);

        WatchEntity watch3 = new WatchEntity("watch3", "user1", "watch2", Arrays.asList("account2", "account4", "account6"));
        watchList.add(watch3);
    }
}
