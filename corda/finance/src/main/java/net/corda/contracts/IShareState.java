package net.corda.contracts;

import net.corda.core.contracts.Amount;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.Issued;
import net.corda.core.contracts.PartyAndReference;
import net.corda.core.crypto.CompositeKey;

import java.time.Instant;
import java.util.Currency;

/**
 * Created by mikecar on 04/04/2017.
 */

public interface IShareState extends ContractState {
    IShareState withOwner(CompositeKey newOwner);

    IShareState withIssuance(PartyAndReference newIssuance);

    IShareState withFaceValue(Amount<Issued<Currency>> newFaceValue);

    IShareState withMaturityDate(Instant newMaturityDate);

    IShareState withQty(Long newQty);

    IShareState withTicker(String newTicker);
}

