package com.angle.trading.bias.model;

import com.angle.trading.marketstructure.model.FairValueGap;
import com.angle.trading.marketstructure.model.LiquidityLevel;
import com.angle.trading.marketstructure.model.LiquiditySweep;
import com.angle.trading.marketstructure.model.OrderBlock;

import java.util.List;

/**
 * Sections 10-12 of the bias sheet — active zones + liquidity.
 * Lists limited to top few nearest to current price for dashboard readability.
 */
public record ZonesSection(
        List<OrderBlock>      activeBullishOBs,
        List<OrderBlock>      activeBearishOBs,
        List<FairValueGap>    activeBullishFvgs,
        List<FairValueGap>    activeBearishFvgs,
        List<LiquidityLevel>  unsweptBSL,          // sorted ascending (nearest above first)
        List<LiquidityLevel>  unsweptSSL,          // sorted descending (nearest below first)
        List<LiquiditySweep>  recentSweeps         // last N
) {}
