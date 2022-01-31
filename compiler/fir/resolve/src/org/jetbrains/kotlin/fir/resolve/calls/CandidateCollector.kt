/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.calls

import org.jetbrains.kotlin.fir.resolve.BodyResolveComponents
import org.jetbrains.kotlin.fir.resolve.calls.tower.TowerGroup
import org.jetbrains.kotlin.resolve.calls.tower.CandidateApplicability
import org.jetbrains.kotlin.resolve.calls.tower.isSuccess
import org.jetbrains.kotlin.resolve.calls.tower.shouldStopResolve

open class CandidateCollector(
    val components: BodyResolveComponents,
    private val resolutionStageRunner: ResolutionStageRunner
) {
    private val groupNumbers = mutableListOf<TowerGroup>()
    private val candidates = mutableListOf<Candidate>()

    var currentApplicability = CandidateApplicability.HIDDEN
        private set

    private var bestGroup = TowerGroup.Last

    fun newDataSet() {
        groupNumbers.clear()
        candidates.clear()
        currentApplicability = CandidateApplicability.HIDDEN
        bestGroup = TowerGroup.Last
    }

    open fun consumeCandidate(group: TowerGroup, candidate: Candidate, context: ResolutionContext): CandidateApplicability {
        val applicability = resolutionStageRunner.processCandidate(candidate, context)

        if (applicability > currentApplicability || (applicability == currentApplicability && group < bestGroup)) {
            candidates.clear()
            currentApplicability = applicability
            bestGroup = group
        }

        if (applicability == currentApplicability && group == bestGroup) {
            candidates.add(candidate)
        }

        return applicability
    }

    fun bestCandidates(): List<Candidate> = candidates

    open fun shouldStopAtTheLevel(group: TowerGroup): Boolean =
        currentApplicability.shouldStopResolve && bestGroup < group

    fun isSuccess(): Boolean {
        return currentApplicability.isSuccess
    }
}

class AllCandidatesCollector(
    components: BodyResolveComponents,
    resolutionStageRunner: ResolutionStageRunner
) : CandidateCollector(components, resolutionStageRunner) {
    private val allCandidatesSet = mutableSetOf<Candidate>()

    override fun consumeCandidate(group: TowerGroup, candidate: Candidate, context: ResolutionContext): CandidateApplicability {
        allCandidatesSet += candidate
        return super.consumeCandidate(group, candidate, context)
    }

    // We want to get candidates at all tower levels.
    override fun shouldStopAtTheLevel(group: TowerGroup): Boolean = false

    val allCandidates: List<Candidate>
        get() = allCandidatesSet.toList()
}
