package com.angle.trading.marketstructure;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.marketstructure.model.Direction;
import com.angle.trading.marketstructure.model.OrderBlock;
import com.angle.trading.marketstructure.model.StructureEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * For every BOS / CHoCH event, find the last opposing candle immediately
 * before the impulse and record its OHLC range as an Order Block.
 *
 *   Bullish event → walk back from the break, find the last DOWN candle
 *                   (close < open). That's the bullish OB.
 *   Bearish event → walk back, find the last UP candle. That's the bearish OB.
 *
 * A DOJI (open == close) is skipped — no clear opposition.
 *
 * Mitigation: after formation, any later candle whose low ≤ top AND high ≥
 * bottom counts as a tap. Records the FIRST such candle only.
 */
@Service
public class OrderBlockDetector {

    public List<OrderBlock> detect(List<Candle> candles, List<StructureEvent> events) {
        List<OrderBlock> out = new ArrayList<>();
        for (StructureEvent event : events) {
            OrderBlock ob = findOrderBlock(candles, event);
            if (ob != null) out.add(ob);
        }
        return out;
    }

    private static OrderBlock findOrderBlock(List<Candle> candles, StructureEvent event) {
        boolean lookForDown = event.direction() == Direction.BULLISH;
        for (int i = event.index() - 1; i >= 0; i--) {
            Candle c = candles.get(i);
            int cmp = c.close().compareTo(c.open());
            boolean isDown = cmp < 0;
            boolean isUp   = cmp > 0;
            if ((lookForDown && isDown) || (!lookForDown && isUp)) {
                return buildOrderBlock(candles, i, event);
            }
        }
        return null;
    }

    private static OrderBlock buildOrderBlock(List<Candle> candles, int obIx, StructureEvent event) {
        Candle ob = candles.get(obIx);
        Integer mitigatedAt = null;
        for (int j = event.index() + 1; j < candles.size(); j++) {
            Candle later = candles.get(j);
            if (later.low().compareTo(ob.high()) <= 0
                    && later.high().compareTo(ob.low()) >= 0) {
                mitigatedAt = j;
                break;
            }
        }
        return new OrderBlock(
                obIx,
                ob.timestamp(),
                ob.high(),
                ob.low(),
                event.direction(),
                obIx,
                event.index(),
                mitigatedAt != null,
                mitigatedAt
        );
    }
}
