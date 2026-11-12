package com.system.app

import android.content.Context

object Rewards {
    fun gain(c: Context, xp: Int, stat: String, pts: Int, reason: String) {
        val before = PlayerStore.read(c)
        PlayerStore.award(c, xp, stat, pts)
        val after = PlayerStore.read(c)
        val extra = if (pts > 0 && stat.isNotEmpty()) ", +$pts $stat" else ""
        SysPopup.post(c, "NOTIFICATION", reason, 1, "+$xp XP$extra")
        if (after.level > before.level) {
            SysPopup.post(c, "NOTIFICATION", "Leveled up!", 1, "LEVEL ${after.level}")
        }
        if (after.rank != before.rank) {
            SysPopup.post(c, "NOTIFICATION", "Rank promoted: ${before.rank} → ${after.rank}", 1, "RANK ${after.rank}")
        }
    }

    fun loss(c: Context, xp: Int, reason: String) {
        val before = PlayerStore.read(c)
        val lost = -PlayerStore.penalty(c, xp)
        val after = PlayerStore.read(c)
        SysPopup.post(c, "NOTIFICATION", reason, 2, if (lost > 0) "-$lost XP" else "XP: 0")
        if (after.level < before.level) {
            SysPopup.post(c, "NOTIFICATION", "Level decreased.", 2, "LEVEL ${after.level}")
        }
        if (after.rank != before.rank) {
            SysPopup.post(c, "NOTIFICATION", "Rank demoted: ${before.rank} → ${after.rank}", 2, "RANK ${after.rank}")
        }
    }
}
