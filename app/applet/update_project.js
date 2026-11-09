const fs = require('fs');

const servicePath = './app/src/main/java/com/example/service/BotService.kt';
const dashboardPath = './app/src/main/java/com/example/ui/BotDashboardScreen.kt';

console.log('Starting programmatic code updates...');

if (fs.existsSync(servicePath)) {
    let content = fs.readFileSync(servicePath, 'utf8');

    // 1. Update 4.0 seconds fast exit rule limit to 2.0 seconds in both mock and real-live trading loops.
    // Replace: val isSixSecondsRuleTriggered = elapsedBoughtSeconds >= 4.0 && actualReturnPercent < 2.0
    content = content.split('elapsedBoughtSeconds >= 4.0').join('elapsedBoughtSeconds >= 2.0');
    console.log('- Updated fast exit time threshold from 4.0s to 2.0s');

    // 2. Locate and append marketCapAtBuy to simulated PENDING log writing
    // We target:
    // val pendingLog = com.example.data.SniperTradeLog(
    //     tokenSymbol = updatedTrack.symbol,
    //     ...
    //     status = "PENDING"
    // )
    const simPendingTarget = `val pendingLog = com.example.data.SniperTradeLog(
                                tokenSymbol = updatedTrack.symbol,
                                tokenMint = updatedTrack.mint,
                                buyDex = buyDex,
                                sellDex = sellDex,
                                buyPriceUsd = buyPrice,
                                sellPriceUsd = 0.0,
                                profitSol = 0.0,
                                profitPercent = 0.0,
                                txSignature = "SimSig_" + kotlin.random.Random.nextInt(100000, 999999),
                                isSimulation = true,
                                ageSecondsAtBuy = creationAgeSeconds,
                                status = "PENDING"
                            )`;
    const simPendingReplacement = `val pendingLog = com.example.data.SniperTradeLog(
                                tokenSymbol = updatedTrack.symbol,
                                tokenMint = updatedTrack.mint,
                                buyDex = buyDex,
                                sellDex = sellDex,
                                buyPriceUsd = buyPrice,
                                sellPriceUsd = 0.0,
                                profitSol = 0.0,
                                profitPercent = 0.0,
                                txSignature = "SimSig_" + kotlin.random.Random.nextInt(100000, 999999),
                                isSimulation = true,
                                ageSecondsAtBuy = creationAgeSeconds,
                                status = "PENDING",
                                marketCapAtBuy = updatedTrack.marketCapUsd
                            )`;
    if (content.includes(simPendingTarget)) {
        content = content.replace(simPendingTarget, simPendingReplacement);
        console.log('- Updated simulated PENDING log to capture marketCapAtBuy');
    } else {
        // Fallback split & join just in case formatting had minor deviations
        content = content.replace(/status = "PENDING"\s*\n\s*\)/g, 'status = "PENDING",\n                                marketCapAtBuy = updatedTrack.marketCapUsd\n                            )');
        console.log('- Dynamic fallback applied for PENDING log marketCapAtBuy injection');
    }

    // 3. Update real transaction PENDING buy logger:
    // val successLog = com.example.data.SniperTradeLog(
    //      tokenSymbol = updatedTrack.symbol,
    //      ...
    //      status = "PENDING"
    // )
    const realPendingTarget = `val successLog = com.example.data.SniperTradeLog(
                                                             tokenSymbol = updatedTrack.symbol,
                                                             tokenMint = updatedTrack.mint,
                                                             buyDex = buyDex,
                                                             sellDex = sellDex,
                                                             buyPriceUsd = buyPrice,
                                                             sellPriceUsd = 0.0,
                                                             profitSol = 0.0,
                                                             profitPercent = 0.0,
                                                             txSignature = sig,
                                                             isSimulation = false,
                                                             ageSecondsAtBuy = creationAgeSeconds,
                                                             status = "PENDING"
                                                        )`;
    const realPendingReplacement = `val successLog = com.example.data.SniperTradeLog(
                                                             tokenSymbol = updatedTrack.symbol,
                                                             tokenMint = updatedTrack.mint,
                                                             buyDex = buyDex,
                                                             sellDex = sellDex,
                                                             buyPriceUsd = buyPrice,
                                                             sellPriceUsd = 0.0,
                                                             profitSol = 0.0,
                                                             profitPercent = 0.0,
                                                             txSignature = sig,
                                                             isSimulation = false,
                                                             ageSecondsAtBuy = creationAgeSeconds,
                                                             status = "PENDING",
                                                             marketCapAtBuy = updatedTrack.marketCapUsd
                                                        )`;
    if (content.includes(realPendingTarget)) {
        content = content.replace(realPendingTarget, realPendingReplacement);
        console.log('- Updated real-live PENDING log to capture marketCapAtBuy');
    }

    // 4. Update SUCCESS simulation trade logger (TakeProfit final outcome)
    const simSuccessTarget = `val pendingLog = repository.getPendingSniperLogForMint(updatedTrack.mint)
                                val sLog = com.example.data.SniperTradeLog(
                                    id = pendingLog?.id ?: 0,
                                    timestamp = pendingLog?.timestamp ?: System.currentTimeMillis(),
                                    tokenSymbol = updatedTrack.symbol,
                                    tokenMint = updatedTrack.mint,
                                    buyDex = updatedTrack.buyDex,
                                    sellDex = sellDex,
                                    buyPriceUsd = updatedTrack.buyPrice,
                                    sellPriceUsd = sellPrice,
                                    profitSol = netReturnSol,
                                    profitPercent = finalProfitPercent,
                                    txSignature = "SimSig_" + kotlin.random.Random.nextInt(100000, 999999),
                                    isSimulation = true,
                                    ageSecondsAtBuy = (System.currentTimeMillis() - updatedTrack.createdAt) / 1000.0,
                                    status = "SUCCESS"
                                )`;
    const simSuccessReplacement = `val pendingLog = repository.getPendingSniperLogForMint(updatedTrack.mint)
                                val sLog = com.example.data.SniperTradeLog(
                                    id = pendingLog?.id ?: 0,
                                    timestamp = pendingLog?.timestamp ?: System.currentTimeMillis(),
                                    tokenSymbol = updatedTrack.symbol,
                                    tokenMint = updatedTrack.mint,
                                    buyDex = updatedTrack.buyDex,
                                    sellDex = sellDex,
                                    buyPriceUsd = updatedTrack.buyPrice,
                                    sellPriceUsd = sellPrice,
                                    profitSol = netReturnSol,
                                    profitPercent = finalProfitPercent,
                                    txSignature = "SimSig_" + kotlin.random.Random.nextInt(100000, 999999),
                                    isSimulation = true,
                                    ageSecondsAtBuy = (System.currentTimeMillis() - updatedTrack.createdAt) / 1000.0,
                                    status = "SUCCESS",
                                    marketCapAtBuy = pendingLog?.marketCapAtBuy ?: updatedTrack.marketCapUsd
                                )`;
    if (content.includes(simSuccessTarget)) {
        content = content.replace(simSuccessTarget, simSuccessReplacement);
        console.log('- Updated sim SUCCESS log to forward marketCapAtBuy');
    }

    // 5. Update STOPLOSS simulation trade logger
    const simStoplossTarget = `val pendingLog = repository.getPendingSniperLogForMint(updatedTrack.mint)
                                val sLog = com.example.data.SniperTradeLog(
                                    id = pendingLog?.id ?: 0,
                                    timestamp = pendingLog?.timestamp ?: System.currentTimeMillis(),
                                    tokenSymbol = updatedTrack.symbol,
                                    tokenMint = updatedTrack.mint,
                                    buyDex = updatedTrack.buyDex,
                                    sellDex = sellDex,
                                    buyPriceUsd = updatedTrack.buyPrice,
                                    sellPriceUsd = sellPrice,
                                    profitSol = -netLossSol,
                                    profitPercent = -finalLossPercent,
                                    txSignature = "SimSig_" + kotlin.random.Random.nextInt(100000, 999999),
                                    isSimulation = true,
                                    ageSecondsAtBuy = (System.currentTimeMillis() - updatedTrack.createdAt) / 1000.0,
                                    status = "STOPLOSS"
                                )`;
    const simStoplossReplacement = `val pendingLog = repository.getPendingSniperLogForMint(updatedTrack.mint)
                                val sLog = com.example.data.SniperTradeLog(
                                    id = pendingLog?.id ?: 0,
                                    timestamp = pendingLog?.timestamp ?: System.currentTimeMillis(),
                                    tokenSymbol = updatedTrack.symbol,
                                    tokenMint = updatedTrack.mint,
                                    buyDex = updatedTrack.buyDex,
                                    sellDex = sellDex,
                                    buyPriceUsd = updatedTrack.buyPrice,
                                    sellPriceUsd = sellPrice,
                                    profitSol = -netLossSol,
                                    profitPercent = -finalLossPercent,
                                    txSignature = "SimSig_" + kotlin.random.Random.nextInt(100000, 999999),
                                    isSimulation = true,
                                    ageSecondsAtBuy = (System.currentTimeMillis() - updatedTrack.createdAt) / 1000.0,
                                    status = "STOPLOSS",
                                    marketCapAtBuy = pendingLog?.marketCapAtBuy ?: updatedTrack.marketCapUsd
                                )`;
    if (content.includes(simStoplossTarget)) {
        content = content.replace(simStoplossTarget, simStoplossReplacement);
        console.log('- Updated sim STOPLOSS log to forward marketCapAtBuy');
    }

    // 6. Update TIMEOUT / FAST_EXIT simulation trade logger
    const simTimeoutTarget = `val pendingLog = repository.getPendingSniperLogForMint(updatedTrack.mint)
                                val sLog = com.example.data.SniperTradeLog(
                                    id = pendingLog?.id ?: 0,
                                    timestamp = pendingLog?.timestamp ?: System.currentTimeMillis(),
                                    tokenSymbol = updatedTrack.symbol,
                                    tokenMint = updatedTrack.mint,
                                    buyDex = updatedTrack.buyDex,
                                    sellDex = sellDex,
                                    buyPriceUsd = updatedTrack.buyPrice,
                                    sellPriceUsd = finalPriceUsed,
                                    profitSol = netOutcomeSol,
                                    profitPercent = finalChangePercent,
                                    txSignature = "SimSigTimed_" + kotlin.random.Random.nextInt(100000, 999999),
                                    isSimulation = true,
                                    ageSecondsAtBuy = (System.currentTimeMillis() - updatedTrack.createdAt) / 1000.0,
                                    status = if (isStagnantStuckTriggered) "STAGNANT" else if (isZigzagDrainTriggered) "ZIGZAG_DRAIN" else if (isSixSecondsRuleTriggered) "FAST_EXIT" else "TIMEOUT"
                                )`;
    const simTimeoutReplacement = `val pendingLog = repository.getPendingSniperLogForMint(updatedTrack.mint)
                                val sLog = com.example.data.SniperTradeLog(
                                    id = pendingLog?.id ?: 0,
                                    timestamp = pendingLog?.timestamp ?: System.currentTimeMillis(),
                                    tokenSymbol = updatedTrack.symbol,
                                    tokenMint = updatedTrack.mint,
                                    buyDex = updatedTrack.buyDex,
                                    sellDex = sellDex,
                                    buyPriceUsd = updatedTrack.buyPrice,
                                    sellPriceUsd = finalPriceUsed,
                                    profitSol = netOutcomeSol,
                                    profitPercent = finalChangePercent,
                                    txSignature = "SimSigTimed_" + kotlin.random.Random.nextInt(100000, 999999),
                                    isSimulation = true,
                                    ageSecondsAtBuy = (System.currentTimeMillis() - updatedTrack.createdAt) / 1000.0,
                                    status = if (isStagnantStuckTriggered) "STAGNANT" else if (isZigzagDrainTriggered) "ZIGZAG_DRAIN" else if (isSixSecondsRuleTriggered) "FAST_EXIT" else "TIMEOUT",
                                    marketCapAtBuy = pendingLog?.marketCapAtBuy ?: updatedTrack.marketCapUsd
                                )`;
    if (content.includes(simTimeoutTarget)) {
        content = content.replace(simTimeoutTarget, simTimeoutReplacement);
        console.log('- Updated sim TIMEOUT / FAST_EXIT log to forward marketCapAtBuy');
    }

    // 7. Update real transaction final (Take profit, stop loss, timeout, fast exit etc) logs:
    const realFinalTarget = `val pendingLog = repository.getPendingSniperLogForMint(updatedTrack.mint)
                                                         val sLog = com.example.data.SniperTradeLog(
                                                             id = pendingLog?.id ?: 0,
                                                             timestamp = pendingLog?.timestamp ?: System.currentTimeMillis(),
                                                             tokenSymbol = updatedTrack.symbol,
                                                             tokenMint = updatedTrack.mint,
                                                             buyDex = updatedTrack.buyDex,
                                                             sellDex = sellDex,
                                                             buyPriceUsd = updatedTrack.buyPrice,
                                                             sellPriceUsd = sellPrice,
                                                             profitSol = finalProfitSol,
                                                             profitPercent = profitPct,
                                                             txSignature = sig,
                                                             isSimulation = false,
                                                             ageSecondsAtBuy = (System.currentTimeMillis() - updatedTrack.createdAt) / 1000.0,
                                                             status = when (tradeAction) {
                                                                 "TAKE_PROFIT" -> "SUCCESS"
                                                                 "STOP_LOSS" -> "STOPLOSS"
                                                                 else -> tradeAction
                                                             }
                                                         )`;
    const realFinalReplacement = `val pendingLog = repository.getPendingSniperLogForMint(updatedTrack.mint)
                                                         val sLog = com.example.data.SniperTradeLog(
                                                             id = pendingLog?.id ?: 0,
                                                             timestamp = pendingLog?.timestamp ?: System.currentTimeMillis(),
                                                             tokenSymbol = updatedTrack.symbol,
                                                             tokenMint = updatedTrack.mint,
                                                             buyDex = updatedTrack.buyDex,
                                                             sellDex = sellDex,
                                                             buyPriceUsd = updatedTrack.buyPrice,
                                                             sellPriceUsd = sellPrice,
                                                             profitSol = finalProfitSol,
                                                             profitPercent = profitPct,
                                                             txSignature = sig,
                                                             isSimulation = false,
                                                             ageSecondsAtBuy = (System.currentTimeMillis() - updatedTrack.createdAt) / 1000.0,
                                                             status = when (tradeAction) {
                                                                 "TAKE_PROFIT" -> "SUCCESS"
                                                                 "STOP_LOSS" -> "STOPLOSS"
                                                                 else -> tradeAction
                                                             },
                                                             marketCapAtBuy = pendingLog?.marketCapAtBuy ?: updatedTrack.marketCapUsd
                                                         )`;
    if (content.includes(realFinalTarget)) {
        content = content.replace(realFinalTarget, realFinalReplacement);
        console.log('- Updated real final trade logs to carry marketCapAtBuy');
    }

    fs.writeFileSync(servicePath, content, 'utf8');
    console.log('BotService.kt programmatic updates complete.');
} else {
    console.warn('BotService.kt path was not found!');
}

if (fs.existsSync(dashboardPath)) {
    let content = fs.readFileSync(dashboardPath, 'utf8');

    // 1. Clean CRLFs & tabulations first to prevent any token mismatch
    content = content.split('\r\n').join('\n');

    // 2. Add formatMarketCap helper function to BotDashboardScreen.kt
    // Let's find an appropriate location, e.g., after standard imports or format utils
    const formatUtilTarget = 'fun safeFormatPrice(price: Double): String {';
    const formatUtilReplacement = `fun formatMarketCap(mcap: Double): String {
    return when {
        mcap >= 1_000_000.0 -> "\${"%.2f".format(mcap / 1_000_000.0)}M"
        mcap >= 1_000.0 -> "\${"%.1f".format(mcap / 1_000.0)}K"
        else -> "%.2f".format(mcap)
    }
}

fun safeFormatPrice(price: Double): String {`;

    if (content.includes(formatUtilTarget) && !content.includes('fun formatMarketCap')) {
        content = content.replace(formatUtilTarget, formatUtilReplacement);
        console.log('- Injected formatMarketCap helper function successfully');
    }

    // 3. Update fast-exit text rule on log items to read (2ث) instead of (4ث)
    content = content.split('خروج سريع (4ث)').join('خروج سريع (2ث)');
    console.log('- Renamed FAST_EXIT label to "خروج سريع (2ث)"');

    // 4. Inject Market Cap badge display for Active/Watched pools (items(trackedTokens))
    // We target: age section
    const watchedAgeTarget = `                                            Text(
                                                text = "عمر العملة: \${"%.1f".format((System.currentTimeMillis() - track.createdAt) / 1000.0)}ث",
                                                color = ElectricBlueGlow,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )`;
    const watchedAgeReplacement = `                                            Text(
                                                text = "عمر العملة: \${"%.1f".format((System.currentTimeMillis() - track.createdAt) / 1000.0)}ث",
                                                color = ElectricBlueGlow,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFFFB020).copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "ماركت كاب: $\${formatMarketCap(track.marketCapUsd)}",
                                                color = Color(0xFFFFB020),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )`;
    if (content.includes(watchedAgeTarget)) {
        content = content.replace(watchedAgeTarget, watchedAgeReplacement);
        console.log('- Injected current Market Cap badge onto watched sniper pools UI list!');
    }

    // 5. Inject Market Cap rendering onto Active Bought positions (under track.hasBought)
    // Target: Text(text = "منصة الشراء الأرخص: ${track.buyDex} ($${"%.5f".format(track.buyPrice)})", ... )
    const boughtDexTarget = `                                            Text(
                                                text = "منصة الشراء الأرخص: \${track.buyDex} ($$\${"%.5f".format(track.buyPrice)})",
                                                color = TextWhite,
                                                fontSize = 10.sp
                                            )`;
    const boughtDexReplacement = `                                            Text(
                                                text = "منصة الشراء الأرخص: \${track.buyDex} ($$\${"%.5f".format(track.buyPrice)})",
                                                color = TextWhite,
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                text = "الماركت كاب الحالي: $\${formatMarketCap(track.marketCapUsd)}",
                                                color = Color(0xFFFFB020),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )`;
    if (content.includes(boughtDexTarget)) {
        content = content.replace(boughtDexTarget, boughtDexReplacement);
        console.log('- Embedded real-time Market Cap dynamically inside the active bought positions block!');
    }

    // 6. Inject Market Cap rendering onto historical trade cards (sold log cards)
    // Target:
    // Text(
    //      text = "شراء من: ${log.buyDex} ($${"%.5f".format(log.buyPriceUsd)})",
    //      color = TechGray,
    //      fontSize = 10.sp
    // )
    const logBuyTarget = `                                Text(
                                    text = "شراء من: \${log.buyDex} ($$\s*\\\${"%.5f".format(log.buyPriceUsd)})",
                                    color = TechGray,
                                    fontSize = 10.sp
                                )`;
    
    const simpleLogBuyTarget = `                                Text(
                                    text = "شراء من: \${log.buyDex} ($$\${"%.5f".format(log.buyPriceUsd)})",
                                    color = TechGray,
                                    fontSize = 10.sp
                                )`;
    const simpleLogBuyReplacement = `                                Text(
                                    text = "شراء من: \${log.buyDex} ($$\${"%.5f".format(log.buyPriceUsd)})",
                                    color = TechGray,
                                    fontSize = 10.sp
                                )
                                if (log.marketCapAtBuy > 0.0) {
                                    Text(
                                        text = "الماركت كاب عند الشراء: $\${formatMarketCap(log.marketCapAtBuy)}",
                                        color = Color(0xFFFFB020),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }`;
    if (content.includes(simpleLogBuyTarget)) {
        content = content.replace(simpleLogBuyTarget, simpleLogBuyReplacement);
        console.log('- Injected historical purchase-time Market Cap display onto trade logs ledger cards!');
    }

    fs.writeFileSync(dashboardPath, content, 'utf8');
    console.log('BotDashboardScreen.kt programmatic updates complete.');
} else {
    console.warn('BotDashboardScreen.kt path was not found!');
}

console.log('Updates complete.');
