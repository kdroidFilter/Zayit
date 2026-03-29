package io.github.kdroidfilter.seforimapp.core.indexing.links

class ShulchanAruchNoseiKelimLinker {
    data class BaseSegment(
        val id: Long,
        val siman: Int,
        val seif: Int,
        val isHagaha: Boolean = false
    )

    data class CommentarySegment(
        val id: Long,
        val siman: Int,
        val seifKatan: String,
        val explicitSeif: Int? = null,
        val explicitHagaha: Boolean? = null
    )

    data class Link(
        val commentarySegmentId: Long,
        val baseSegmentId: Long
    )

    fun link(
        baseSegments: List<BaseSegment>,
        commentarySegments: List<CommentarySegment>
    ): List<Link> {
        if (baseSegments.isEmpty() || commentarySegments.isEmpty()) return emptyList()

        val baseBySiman = baseSegments.groupBy { it.siman }
        val commentaryBySiman = commentarySegments.groupBy { it.siman }
        val links = ArrayList<Link>()

        for ((siman, simanCommentary) in commentaryBySiman) {
            val simanBase = baseBySiman[siman].orEmpty()
            if (simanBase.isEmpty()) continue

            val sortedCommentary = simanCommentary.sortedBy { parseSeifKatan(it.seifKatan) }
            val resolvedTargets = MutableList<BaseSegment?>(sortedCommentary.size) { null }

            sortedCommentary.forEachIndexed { index, segment ->
                val target = resolveExplicitTarget(segment, simanBase)
                if (target != null) {
                    resolvedTargets[index] = target
                }
            }

            propagateResolvedTargets(simanBase, resolvedTargets)

            sortedCommentary.forEachIndexed { index, commentary ->
                val target = resolvedTargets[index] ?: return@forEachIndexed
                links += Link(
                    commentarySegmentId = commentary.id,
                    baseSegmentId = target.id
                )
            }
        }

        return links
    }

    private fun propagateResolvedTargets(
        simanBase: List<BaseSegment>,
        resolvedTargets: MutableList<BaseSegment?>
    ) {
        if (resolvedTargets.isEmpty()) return

        var firstResolvedIndex = -1
        for (i in resolvedTargets.indices) {
            if (resolvedTargets[i] != null) {
                firstResolvedIndex = i
                break
            }
        }

        if (firstResolvedIndex == -1) {
            val defaultTarget = defaultBaseTarget(simanBase) ?: return
            for (i in resolvedTargets.indices) {
                resolvedTargets[i] = defaultTarget
            }
            return
        }

        val leadingTarget = resolvedTargets[firstResolvedIndex] ?: defaultBaseTarget(simanBase)
        if (leadingTarget != null) {
            for (i in 0 until firstResolvedIndex) {
                if (resolvedTargets[i] == null) {
                    resolvedTargets[i] = leadingTarget
                }
            }
        }

        var carry: BaseSegment? = null
        for (i in resolvedTargets.indices) {
            val current = resolvedTargets[i]
            if (current != null) {
                carry = current
            } else if (carry != null) {
                resolvedTargets[i] = carry
            }
        }

        carry = null
        for (i in resolvedTargets.lastIndex downTo 0) {
            val current = resolvedTargets[i]
            if (current != null) {
                carry = current
            } else if (carry != null) {
                resolvedTargets[i] = carry
            }
        }

        val fallback = defaultBaseTarget(simanBase) ?: return
        for (i in resolvedTargets.indices) {
            if (resolvedTargets[i] == null) {
                resolvedTargets[i] = fallback
            }
        }
    }

    private fun resolveExplicitTarget(
        segment: CommentarySegment,
        simanBase: List<BaseSegment>
    ): BaseSegment? {
        val explicitSeif = segment.explicitSeif ?: return null
        val explicitHagaha = segment.explicitHagaha

        if (explicitHagaha != null) {
            return simanBase.firstOrNull { it.seif == explicitSeif && it.isHagaha == explicitHagaha }
                ?: simanBase.firstOrNull { it.seif == explicitSeif }
        }

        return simanBase.firstOrNull { it.seif == explicitSeif && !it.isHagaha }
            ?: simanBase.firstOrNull { it.seif == explicitSeif }
    }

    private fun defaultBaseTarget(simanBase: List<BaseSegment>): BaseSegment? {
        return simanBase.firstOrNull { it.seif == 1 && !it.isHagaha }
            ?: simanBase.firstOrNull { it.seif == 1 }
            ?: simanBase.minByOrNull { it.seif }
    }

    private fun parseSeifKatan(raw: String): Int {
        val cleaned = raw
            .replace("\"", "")
            .replace("׳", "")
            .replace("״", "")
            .replace("'", "")
            .trim()

        if (cleaned.isEmpty()) return Int.MAX_VALUE

        cleaned.toIntOrNull()?.let { return it }

        var sum = 0
        cleaned.forEach { c ->
            sum += when (c) {
                'א' -> 1
                'ב' -> 2
                'ג' -> 3
                'ד' -> 4
                'ה' -> 5
                'ו' -> 6
                'ז' -> 7
                'ח' -> 8
                'ט' -> 9
                'י' -> 10
                'כ', 'ך' -> 20
                'ל' -> 30
                'מ', 'ם' -> 40
                'נ', 'ן' -> 50
                'ס' -> 60
                'ע' -> 70
                'פ', 'ף' -> 80
                'צ', 'ץ' -> 90
                'ק' -> 100
                'ר' -> 200
                'ש' -> 300
                'ת' -> 400
                else -> 0
            }
        }

        return if (sum == 0) Int.MAX_VALUE else sum
    }
}
