package com.example.sheetmusicapp.scoreModel

import java.io.Serializable
import java.lang.IllegalArgumentException
import kotlin.math.max

enum class StemDirection{
    UP,
    DOWN
}

/**
 * A voice of a [Bar], i.e. (1) a group of [RhythmicInterval] instances in [intervals] of which notes should
 * connect their stems in some cases, and should have the same stem direction when there are multiple [Voice] instances
 * in a bar, and (2) the grouping of those intervals into [SubGroup] instances in [subGroups], in which intervals
 * can freely decide on a common stem direction when there are no other voices.
 * When visualizing intervals, padding width for the space between subgroups should be added to the width of the last interval
 * of each subgroup (see [calculatePaddingFactor]).
 *
 * @property intervals Intervals contained by the voice from which sub groups are constructed.
 * @property timeSignature Time signature of the bar the voice is in. Decides how sub groups are constructed.
 * @property subGroups Accessible from outside the voice instance via [getCopyOfSubGroups] to prevent outside manipulation.
 * Need to be recalculated (starting from some interval) after changing the voice's intervals via [recalculateSubGroupsFrom].
 * @property intervalSubGroupIdxs Inversion of the SubGroup -> List<RhythmicInterval> relation to ease its internal recalculation.
 * Accessible from outside the voice instance via [getIntervalSubGroupIdxsCopy] to prevent outside manipulation.
 * @throws IllegalArgumentException When [intervals] is empty. The "emptiest" voices that should be created should contain rests at least.
 * @throws IllegalArgumentException When one element of [intervals] exceeds the length of units of the given [TimeSignature].
 */
class Voice (val intervals: MutableList<RhythmicInterval>, var timeSignature: TimeSignature) : Serializable{

    private lateinit var subGroups: List<SubGroup>
    private val intervalSubGroupIdxs: MutableMap<RhythmicInterval, Int> = mutableMapOf()
    // Can be set by the containing bar.
    var stemDirection : StemDirection? = null

    init {
        if (intervals.isEmpty()){
            throw IllegalArgumentException("Voices without intervals should not be created!")
        }
        initializeSubGroups()
    }

    fun getCopyOfSubGroups(): MutableList<SubGroup>{
        return subGroups.toMutableList()
    }

    fun getIntervalSubGroupIdxsCopy(): MutableMap<RhythmicInterval, Int> {
        return intervalSubGroupIdxs.toMutableMap()
    }

    /**
     * Sorts all contained intervals in their subgroups, created according to [timeSignature].
     * Also creates the according inverse mapping for [intervalSubGroupIdxs].
     */
    fun initializeSubGroups(){
        // Sub group initialization //
        val subGroupEndUnits = timeSignature.subGroupEndUnits
        val subGroupAggregator = mutableListOf<SubGroup>()
        var intervalIdx = 0
        var currentInterval = intervals[intervalIdx]
        // Iterate over number of sub groups (& intervals)
        for (i in 0 until timeSignature.numberOfSubGroups){

            val currentSubGroup: SubGroup
            // Create sub group instance with according start & end unit.
            if (i == 0){
                currentSubGroup = SubGroup(mutableListOf<RhythmicInterval>(), 1 ,subGroupEndUnits[i])
            }
            else {
                currentSubGroup = SubGroup(mutableListOf<RhythmicInterval>(), subGroupEndUnits[i - 1] + 1, subGroupEndUnits[i])
            }

            // Error detection
            if (currentInterval.endUnit > timeSignature.units){
                throw IllegalArgumentException("Interval $i exceeds the time signature's length.")
            }
            // Iterate over intervals: Add all that should belong to this sub group.
            // Stop when first interval is in another.
            while (timeSignature.calculateSubGroup(currentInterval) == i){
                currentSubGroup.add(currentInterval)
                intervalSubGroupIdxs[currentInterval] = i
                intervalIdx++
                if (intervalIdx == intervals.size){
                    break
                }
                currentInterval = intervals[intervalIdx]
            }
            calculatePaddingFactor(currentSubGroup, i)
            currentSubGroup.calculateConnectedIntervals()
            subGroupAggregator.add(currentSubGroup)
        }
        subGroups = subGroupAggregator.toList()
    }

    /**
     * Calculates how many times the padding width for inbetween sub groups should be added to the width
     * of a sub groups last interval when visualizing the bar, based on how many sub groups the interval stretches.
     * Sets this value as paddingFactor of the given [SubGroup].
     *
     * The padding should not be added after the last sub group, therefore its padding factor will always be 0.
     * Otherwise, at least one such padding always follows the last interval of a sub group or is part of it (in sheet music),
     * therefore the padding factor will be at least 1.
     *
     * @param subGroup The sub group of which the padding factor should be set.
     * @param subGroupIdx The (future) position of the given sub group in the voice.
     * @throws IllegalArgumentException When [subGroupIdx] is negative or exceeds the sub group number a bar of [timeSignature] should have.
     */
    private fun calculatePaddingFactor(subGroup: SubGroup, subGroupIdx: Int){

        if (subGroupIdx >= timeSignature.numberOfSubGroups || subGroupIdx < 0){
            throw IllegalArgumentException("The given sub group index exceeds the sub groups in a bar of the time signature, or is negative!")
        }

        // Set minimum padding => 1 for non-last sub groups, 0 for last
        val minimumPaddingFactor = if(subGroupIdx < timeSignature.numberOfSubGroups - 1) 1 else 0

        // Find the last interval of the sub group, i.e. the one that has the last end unit.
        val subGroupIntervals = subGroup.getCopyOfIntervals()
        var lastInterval: RhythmicInterval? = null
        subGroupIntervals.forEach { interval ->
            val currentLargest = lastInterval
            if (currentLargest != null){
                if (interval.endUnit > currentLargest.endUnit){
                       lastInterval = interval
                }
            }
            else {
                lastInterval = interval
            }
        }

        val actualLast = lastInterval
        // Set minimum padding factor if no intervals are contained-
        if (actualLast == null){
            subGroup.paddingFactor = minimumPaddingFactor
        }
        // Otherwise, set minimum or the amount of different sub groups the interval stretches to, if the latter is larger.
        // calculateSubGroup / calculateLastCoveredSubGroup throws exceptions for intervals exceeding the bar and therefore
        // all sub groups. Such intervals should not occur in sub groups-
        else {
            val subGroupDifference : Int = timeSignature.calculateLastCoveredSubGroup(actualLast.endUnit) - timeSignature.calculateSubGroup(actualLast)
            subGroup.paddingFactor = max(subGroupDifference, minimumPaddingFactor)
        }
        subGroup.lastInterval = actualLast
    }

    /**
     * Calculates the average height of all notes of all intervals of the sub groups of the voice.
     * @return null when no notes are present (voice only consists of rests), the average otherwise
     */
    fun getAvgNoteHeight() : Double? {
        var heightSum = 0
        var notesCount = 0
        for (interval in intervals){
            for (noteHeight in interval.getNoteHeadsCopy().keys){
                heightSum += noteHeight
                notesCount++
            }
        }
        if (notesCount == 0) return null
        else return heightSum / notesCount.toDouble()
    }

    /**
     * Reassigns intervals from [intervals] to sub groups, starting from some [intervalIdx]. In consequence,
     * [SubGroup] properties depending on a group's intervals are also recalculated.
     * @throws IllegalArgumentException When the given index exceeds [intervals].
     */
    fun recalculateSubGroupsFrom(intervalIdx: Int){

        if (intervalIdx >= intervals.size){
            throw IllegalArgumentException("Given index exceeds interval list!")
        }

        var firstSubGroupIdx : Int? = null
        for (i in intervalIdx until intervals.size){
            val interval = intervals[i]
            val intervalSubGroupIdx = intervalSubGroupIdxs[interval]
            val newSubGroupIdx = timeSignature.calculateSubGroup(interval)
            if (i == intervalIdx){
                firstSubGroupIdx = newSubGroupIdx
            }

            // Only add: When interval was not part of a sub group before, i.e. was newly added.
            if (intervalSubGroupIdx == null){
                subGroups[newSubGroupIdx].add(interval)
            }
            else {
                // If interval changes subgroup, remove it from previous and add to new
                if (intervalSubGroupIdx != newSubGroupIdx){
                    subGroups[intervalSubGroupIdx].remove(interval)
                    subGroups[newSubGroupIdx].add(interval)
                }
                // otherwise, the interval does not change its subgroup
            }
            // Also assignment as inverse relationship
            intervalSubGroupIdxs[interval] = newSubGroupIdx
        }

        // Recalculate padding factor and remove assigned intervals that were removed from this.intervals for all sub groups.
        if (firstSubGroupIdx != null) {
            for (i in firstSubGroupIdx until subGroups.size) {
                val currentSubGroup = subGroups[i]
                currentSubGroup.getCopyOfIntervals().forEach { interval ->
                    if (!intervals.contains(interval)) {
                        currentSubGroup.remove(interval)
                        intervalSubGroupIdxs.remove(interval)
                    }
                }
                calculatePaddingFactor(currentSubGroup, i)
                currentSubGroup.calculateConnectedIntervals()
            }
        }
        else throw IllegalStateException("The interval at the given idx is not part of a subgroup!")
    }

    /**
     * Returns true if the voice only contains rests, false otherwise.
     */
    fun isVoiceOfRests() : Boolean {
        return getAvgNoteHeight() == null
    }
}

/**
 * List of intervals of a subsection of a [Voice] of a [Bar].
 * Calculates average height of contained notes to enable sub group intern decisions on if all stems of the associated notes
 * should face upwards or downwards.
 * [paddingFactor] enables adding of padding widths when displaying the last interval of a subgroup, based on how many
 * subgroup it stretches, calculated by the [Voice] the sub group's part of.
 * [startUnit] and [endUnit] should be derived from voice time signature before construction.
 *
 * Automatically updated by [SubGroup.add] and [SubGroup.remove]. Null when empty.
 * @property paddingFactor Specifies how many times the width of a padding between subgroups should be added
 * to the musical length-based UI width of the last interval of the sub group when calculating note
 * positions iteratively. Is calculated by the [Voice] a sub group is part of.
 * @property connectedIntervals Lists of intervals which should be connected horizontally when visualized, from
 * [calculateConnectedIntervals]
 * @throws IllegalArgumentException When a given interval exceeds the given [startUnit] or [endUnit].
 */
class SubGroup (private val intervals: MutableList<RhythmicInterval>, private val startUnit: Int, private val endUnit: Int) : Serializable{

    var paddingFactor : Int = 0
    var lastInterval : RhythmicInterval? = null
    val connectedIntervals : MutableList<MutableList<RhythmicInterval>> = mutableListOf()

    init {
        // Error detection
        for (i in 0 until intervals.size) {
            if (intervals[i].startUnit < startUnit || intervals[i].startUnit > endUnit){
                throw IllegalArgumentException("Interval $i doesn't start in the sub group's units.")
            }
        }
    }

    fun getCopyOfIntervals() : MutableList<RhythmicInterval> {return intervals.toMutableList()}

    /**
     * Wrapper for adding an interval to [intervals] that provides error detection on input.
     *
     * @param interval A [RhythmicInterval] instance which should be positioned the sub group's start and end unit
     * and is not already part of [intervals].
     * @throws IllegalArgumentException When the given interval is not positioned between the group's start and
     * end unit or is already part of [intervals].
     */
    fun add(interval: RhythmicInterval) {
        if (interval.startUnit < startUnit || interval.startUnit > endUnit){
            throw IllegalArgumentException("The given interval doesn't start in the sub group's units.")
        }
        if (intervals.contains(interval)){
            throw IllegalArgumentException("The given interval is already part of the sub group!")
        }

        intervals.add(interval)
    }

    /**
     * Wrapper for removing an interval from [intervals] that provides error detection on input.
     *
     * @param interval A [RhythmicInterval] instance which should be part of the subgroup.
     * @throws IllegalArgumentException When the given interval is not part of the subgroup-
     */
    fun remove(interval: RhythmicInterval) {
        val removed = intervals.remove(interval)
        if (!removed){
            throw IllegalArgumentException("The given interval is not in the sub group!")
        }
    }

    /**
     * Returns the common stem direction for all notes of the subgroup, based on their height average.
     * Notes equal to or smaller than 6.5 will face upwards, the others downwards.
     */
    fun getStemDirection() : StemDirection {
        val noteHeightAvg = getAvgNoteHeight() ?: return StemDirection.UP
        return if (noteHeightAvg <= 6.5) StemDirection.UP else StemDirection.DOWN
    }

    /**
     * Returns the height average of the notes of all contained intervals, or null if there are
     * no notes (or intervals).
     */
    private fun getAvgNoteHeight() : Double? {
        val noteHeightSum = calculateNoteHeightSum()
        if (noteHeightSum == 0){
            return null
        }
        else{
            return noteHeightSum / getNotesCount().toDouble()
        }
    }

    /**
     * Returns the sum of the notes of all contained intervals, or null if there are no notes.
     */
    private fun calculateNoteHeightSum() : Int{
        var sum = 0
        for (interval in intervals){
            for (noteHeadHeight in interval.getNoteHeadsCopy().keys){
                sum += noteHeadHeight
            }
        }
        return sum
    }

    private fun getNotesCount(): Int {
        var count = 0
        for (interval in intervals){
            count += interval.getNoteHeadsCopy().keys.size
        }
        return count
    }

    /**
     * Compares the given interval with [lastInterval] (set on changes by [Voice] the sub group is part of),
     * and returns the result.
     * @throws IllegalArgumentException When the given interval is not part of the sub group.
     */
    fun isLast(interval: RhythmicInterval) : Boolean {
        if (!intervals.contains(interval)){
            throw IllegalArgumentException("The given interval is not part of the sub group!")
        }
        return interval == lastInterval
    }

    /**
     * Creates lists of contained intervals which should be connected horizontally, depending
     * on which rhythmic lengths follow after another when the intervals are ordered by end unit.
     * Lists which only contain one interval which could potentially connected will be dismissed,
     * only at least two intervals can be connected.
     */
    fun calculateConnectedIntervals(){
        connectedIntervals.clear()
        var newConnectedList = mutableListOf<RhythmicInterval>()
        val sortedIntervals = intervals.sortedBy { it.endUnit }

        // Iterate over intervals and compare with previous / successive intervals.
        for (interval in sortedIntervals){

            // Rests do not have connections.
            if (interval.isRest){
                if (newConnectedList.isNotEmpty()){
                    if (newConnectedList.size > 1){
                        connectedIntervals.add(newConnectedList)
                    }
                    newConnectedList = mutableListOf<RhythmicInterval>()
                }
            }
            else {
                val length = interval.getLengthCopy()
                // 8ths and 16ths are potentially connected to following intervals
                if (newConnectedList.isEmpty()) {
                    if (length.basicLength in listOf(BasicRhythmicLength.EIGHTH, BasicRhythmicLength.SIXTEENTH)) {
                        newConnectedList.add(interval)
                    }
                } else {
                    val lastConnectedLength = newConnectedList.last().getLengthCopy()
                    when (lastConnectedLength.lengthInUnits) {

                        RhythmicLength(BasicRhythmicLength.EIGHTH).lengthInUnits ->
                            // 8ths and 16ths should be connected to previous 8ths
                            if (length.lengthInUnits in listOf(RhythmicLength(BasicRhythmicLength.EIGHTH).lengthInUnits, RhythmicLength(BasicRhythmicLength.SIXTEENTH).lengthInUnits)) {
                                newConnectedList.add(interval)
                            } else {
                                if (newConnectedList.size > 1){
                                    connectedIntervals.add(newConnectedList)
                                }
                                newConnectedList = mutableListOf<RhythmicInterval>()
                            }

                        RhythmicLength(BasicRhythmicLength.EIGHTH, LengthModifier.DOTTED).lengthInUnits ->
                            // 16ths should be connected to previous dotted 8ths
                            if (length.lengthInUnits == RhythmicLength(BasicRhythmicLength.SIXTEENTH).lengthInUnits) {
                                newConnectedList.add(interval)
                            } else {
                                if (newConnectedList.size > 1){
                                    connectedIntervals.add(newConnectedList)
                                }
                                newConnectedList = mutableListOf<RhythmicInterval>()
                            }

                        RhythmicLength(BasicRhythmicLength.SIXTEENTH).lengthInUnits ->
                            // 8ths, dotted 8ths and 16ths should be connected to previous 16ths
                            if (length.lengthInUnits in listOf(RhythmicLength(BasicRhythmicLength.EIGHTH).lengthInUnits,
                                            RhythmicLength(BasicRhythmicLength.SIXTEENTH).lengthInUnits,
                                            RhythmicLength(BasicRhythmicLength.EIGHTH, LengthModifier.DOTTED))) {
                                newConnectedList.add(interval)
                            } else {
                                if (newConnectedList.size > 1){
                                    connectedIntervals.add(newConnectedList)
                                }
                                newConnectedList = mutableListOf<RhythmicInterval>()
                            }
                    }
                }
            }
        }
        // Add potential list after all intervals have been seen.
        if (newConnectedList.size > 1){
            connectedIntervals.add(newConnectedList)
        }
    }

}