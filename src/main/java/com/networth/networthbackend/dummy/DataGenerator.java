package com.networth.networthbackend.dummy;


import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.AccountTransactionEntity;
import com.networth.networthbackend.entity.WatchEntity;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class DataGenerator {

    public final List<AccountEntity> accountList = new ArrayList<>();
    public final List<AccountBrokerEntity> accountBrokerEntityList = new ArrayList<>();
    public final List<AccountTransactionEntity> accountTransactionList = new ArrayList<>();
    public final List<WatchEntity> watchList = new ArrayList<>();

    public DataGenerator() {
//        generateAccountData();
//        generateAccountBrokerData();
//        generateAccountTransactionData();
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
        AccountBrokerEntity accountBroker1 = new AccountBrokerEntity("accountBroker1", "account5", "NOW", "");
        AccountBrokerEntity accountBroker2 = new AccountBrokerEntity("accountBroker2", "account5", "AAPL", "");
        accountBrokerEntityList.add(accountBroker1);
        accountBrokerEntityList.add(accountBroker2);


        AccountBrokerEntity accountBroker3 = new AccountBrokerEntity("accountBroker3", "account6", "0P00011MAX.BO", "");
        AccountBrokerEntity accountBroker4 = new AccountBrokerEntity("accountBroker4", "account6", "AMZN", "");
        accountBrokerEntityList.add(accountBroker3);
        accountBrokerEntityList.add(accountBroker4);
    }

    private void generateAccountTransactionData() {
        AccountTransactionEntity accountTransaction1 = new AccountTransactionEntity("accountTransaction1", "account1", "2024-01-01 10:00:00 UTC", 10.0, true);
        AccountTransactionEntity accountTransaction2 = new AccountTransactionEntity("accountTransaction2", "account1", "2024-02-01 10:00:00 UTC", 20.0, true);
        AccountTransactionEntity accountTransaction3 = new AccountTransactionEntity("accountTransaction3", "account1", "2024-03-01 10:00:00 UTC", 30.0, true);
        AccountTransactionEntity accountTransaction4 = new AccountTransactionEntity("accountTransaction4", "account1", "2024-04-01 10:00:00 UTC", 40.0, true);
        AccountTransactionEntity accountTransaction5 = new AccountTransactionEntity("accountTransaction5", "account1", "2024-05-01 10:00:00 UTC", 50.0, true);
        accountTransactionList.add(accountTransaction1);
        accountTransactionList.add(accountTransaction2);
        accountTransactionList.add(accountTransaction3);
        accountTransactionList.add(accountTransaction4);
        accountTransactionList.add(accountTransaction5);

        AccountTransactionEntity accountTransaction6 = new AccountTransactionEntity("accountTransaction6", "account2", "2024-01-01 10:00:00 UTC", 10.0, true);
        AccountTransactionEntity accountTransaction7 = new AccountTransactionEntity("accountTransaction7", "account2", "2024-02-01 10:00:00 UTC", 20.0, true);
        AccountTransactionEntity accountTransaction8 = new AccountTransactionEntity("accountTransaction8", "account2", "2024-03-01 10:00:00 UTC", 30.0, true);
        AccountTransactionEntity accountTransaction9 = new AccountTransactionEntity("accountTransaction9", "account2", "2024-04-01 10:00:00 UTC", 40.0, true);
        AccountTransactionEntity accountTransaction10 = new AccountTransactionEntity("accountTransaction10", "account2", "2024-05-01 10:00:00 UTC", 50.0, true);
        accountTransactionList.add(accountTransaction6);
        accountTransactionList.add(accountTransaction7);
        accountTransactionList.add(accountTransaction8);
        accountTransactionList.add(accountTransaction9);
        accountTransactionList.add(accountTransaction10);

        AccountTransactionEntity accountTransaction11 = new AccountTransactionEntity("accountTransaction11", "account3", "2024-01-01 10:00:00 UTC", 10.0, true);
        AccountTransactionEntity accountTransaction12 = new AccountTransactionEntity("accountTransaction12", "account3", "2024-02-01 10:00:00 UTC", 20.0, true);
        AccountTransactionEntity accountTransaction13 = new AccountTransactionEntity("accountTransaction13", "account3", "2024-03-01 10:00:00 UTC", 30.0, true);
        AccountTransactionEntity accountTransaction14 = new AccountTransactionEntity("accountTransaction14", "account3", "2024-04-01 10:00:00 UTC", 40.0, true);
        AccountTransactionEntity accountTransaction15 = new AccountTransactionEntity("accountTransaction15", "account3", "2024-05-01 10:00:00 UTC", 50.0, true);
        accountTransactionList.add(accountTransaction11);
        accountTransactionList.add(accountTransaction12);
        accountTransactionList.add(accountTransaction13);
        accountTransactionList.add(accountTransaction14);
        accountTransactionList.add(accountTransaction15);

        AccountTransactionEntity accountTransaction16 = new AccountTransactionEntity("accountTransaction16", "account4", "2024-01-01 10:00:00 UTC", 10.0, true);
        AccountTransactionEntity accountTransaction17 = new AccountTransactionEntity("accountTransaction17", "account4", "2024-02-01 10:00:00 UTC", 20.0, true);
        AccountTransactionEntity accountTransaction18 = new AccountTransactionEntity("accountTransaction18", "account4", "2024-03-01 10:00:00 UTC", 30.0, true);
        AccountTransactionEntity accountTransaction19 = new AccountTransactionEntity("accountTransaction19", "account4", "2024-04-01 10:00:00 UTC", 40.0, true);
        AccountTransactionEntity accountTransaction20 = new AccountTransactionEntity("accountTransaction20", "account4", "2024-05-01 10:00:00 UTC", 50.0, true);
        accountTransactionList.add(accountTransaction16);
        accountTransactionList.add(accountTransaction17);
        accountTransactionList.add(accountTransaction18);
        accountTransactionList.add(accountTransaction19);
        accountTransactionList.add(accountTransaction20);

        AccountTransactionEntity accountTransaction21 = new AccountTransactionEntity("accountTransaction21", "accountBroker1", "2019-01-01 10:00:00 UTC", 10.0, true);
        AccountTransactionEntity accountTransaction22 = new AccountTransactionEntity("accountTransaction22", "accountBroker1", "2024-02-01 10:00:00 UTC", 20.0, true);
        AccountTransactionEntity accountTransaction23 = new AccountTransactionEntity("accountTransaction23", "accountBroker1", "2024-03-01 10:00:00 UTC", 30.0, true);
        AccountTransactionEntity accountTransaction24 = new AccountTransactionEntity("accountTransaction24", "accountBroker1", "2024-04-01 10:00:00 UTC", 40.0, true);
        AccountTransactionEntity accountTransaction25 = new AccountTransactionEntity("accountTransaction25", "accountBroker1", "2024-05-01 10:00:00 UTC", 00.0, true);
        accountTransactionList.add(accountTransaction21);
        accountTransactionList.add(accountTransaction22);
        accountTransactionList.add(accountTransaction23);
        accountTransactionList.add(accountTransaction24);
        accountTransactionList.add(accountTransaction25);

        AccountTransactionEntity accountTransaction26 = new AccountTransactionEntity("accountTransaction26", "accountBroker2", "2019-01-01 10:00:00 UTC", 10.0, true);
        AccountTransactionEntity accountTransaction27 = new AccountTransactionEntity("accountTransaction27", "accountBroker2", "2024-02-01 10:00:00 UTC", 20.0, true);
        AccountTransactionEntity accountTransaction28 = new AccountTransactionEntity("accountTransaction28", "accountBroker2", "2024-03-01 10:00:00 UTC", 30.0, true);
        AccountTransactionEntity accountTransaction29 = new AccountTransactionEntity("accountTransaction29", "accountBroker2", "2024-04-01 10:00:00 UTC", 40.0, true);
        AccountTransactionEntity accountTransaction30 = new AccountTransactionEntity("accountTransaction30", "accountBroker2", "2024-05-01 10:00:00 UTC", 50.0, true);
        accountTransactionList.add(accountTransaction26);
        accountTransactionList.add(accountTransaction27);
        accountTransactionList.add(accountTransaction28);
        accountTransactionList.add(accountTransaction29);
        accountTransactionList.add(accountTransaction30);

        AccountTransactionEntity accountTransaction31 = new AccountTransactionEntity("accountTransaction31", "accountBroker3", "2019-01-01 10:00:00 UTC", 10.0, true);
        AccountTransactionEntity accountTransaction32 = new AccountTransactionEntity("accountTransaction32", "accountBroker3", "2024-02-01 10:00:00 UTC", 20.0, true);
        AccountTransactionEntity accountTransaction33 = new AccountTransactionEntity("accountTransaction33", "accountBroker3", "2024-03-01 10:00:00 UTC", 30.0, true);
        AccountTransactionEntity accountTransaction34 = new AccountTransactionEntity("accountTransaction34", "accountBroker3", "2024-04-01 10:00:00 UTC", 40.0, true);
        AccountTransactionEntity accountTransaction35 = new AccountTransactionEntity("accountTransaction35", "accountBroker3", "2024-05-01 10:00:00 UTC", 0.0, true);
        accountTransactionList.add(accountTransaction31);
        accountTransactionList.add(accountTransaction32);
        accountTransactionList.add(accountTransaction33);
        accountTransactionList.add(accountTransaction34);
        accountTransactionList.add(accountTransaction35);

        AccountTransactionEntity accountTransaction36 = new AccountTransactionEntity("accountTransaction36", "accountBroker4", "2019-01-01 10:00:00 UTC", 10.0, true);
        AccountTransactionEntity accountTransaction37 = new AccountTransactionEntity("accountTransaction37", "accountBroker4", "2024-02-01 10:00:00 UTC", 20.0, true);
        AccountTransactionEntity accountTransaction38 = new AccountTransactionEntity("accountTransaction38", "accountBroker4", "2024-03-01 10:00:00 UTC", 30.0, true);
        AccountTransactionEntity accountTransaction39 = new AccountTransactionEntity("accountTransaction39", "accountBroker4", "2024-04-01 10:00:00 UTC", 40.0, true);
        AccountTransactionEntity accountTransaction40 = new AccountTransactionEntity("accountTransaction40", "accountBroker4", "2024-05-01 10:00:00 UTC", 50.0, true);
        accountTransactionList.add(accountTransaction36);
        accountTransactionList.add(accountTransaction37);
        accountTransactionList.add(accountTransaction38);
        accountTransactionList.add(accountTransaction39);
        accountTransactionList.add(accountTransaction40);
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
