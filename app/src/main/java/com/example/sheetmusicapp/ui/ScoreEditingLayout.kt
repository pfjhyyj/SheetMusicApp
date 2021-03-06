package com.example.sheetmusicapp.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.doOnLayout
import com.example.sheetmusicapp.MainActivity
import com.example.sheetmusicapp.scoreModel.*
import java.lang.ClassCastException
import kotlin.IllegalArgumentException

/**
 * ConstraintLayout containing an editable score, i.e. a [Score] instance, a [BarVisLayout]
 * displaying the contents of a currently selected bar and 4 [BarEditingOverlayLayout]s via which a user can manipulate
 * the contents of the current bar, one for each voice a bar can have.
 *
 * @param prevBarButton Previous bar button in main activity which needs to be disabled / enabled depending on if the
 * current bar is the first one.
 * @param barHeight for vertical division into grid in the [BarEditingOverlayLayout]s
 * @throws IllegalArgumentException When the given Score doesn't contain any bars.
 * @throws IllegalArgumentException When the given initBarIdx exceeds the score bar list.
 */
class ScoreEditingLayout (context: Context, private val prevBarButton: ImageButton, private val barHeight: Int, val score: Score, initBarIdx : Int = 0) : ConstraintLayout(context) {

    val voiceGridOverlays : MutableMap<Int, BarEditingOverlayLayout> = mutableMapOf()
    var activeVoiceOverlayNum = 1
    var barVisLayout : BarVisLayout? = null
    
    val bars = score.barList
    var previousButtonDisabled = initBarIdx == 0
    
    var bar : Bar = run {
        if (bars.size <= 0){
            throw IllegalArgumentException("Don't create empty scores!")
        }
        if (initBarIdx >= bars.size){
            throw IllegalArgumentException("initBarIdx exceeds score bar list!")
        }
        bars[initBarIdx]
    }
        // bar set //
        set(value) {
            val currentBarVisLayout = barVisLayout
                    ?: throw IllegalStateException("Bar can't be reset if a bar visualization layout wasn't added yet!")
            currentBarVisLayout.bar = value
            field = value
        }
    var barIdx = initBarIdx


    init {
        // Initialize layouts.
        val newBarVisLayout = addBarVisLayout(bar)
        for (i in 1..4){
            val newOverlay = addBarEditingOverlayLayout()
            voiceGridOverlays[i] = newOverlay
            // set listener for note input / deletion to main activity, which needs to evaluate input configuration
            newOverlay.listener = try {
                context as MainActivity
            }
            catch (e: ClassCastException){
                throw IllegalStateException("Context must be MainActivity!")
            }
            newOverlay.visibility = INVISIBLE
        }
        // only show first voice
        voiceGridOverlays[1]?.visibility = VISIBLE
        // set listener of barVisLayout to update the voiceGridOverlays' horizontal margins
        // according to voice intervals
        newBarVisLayout.setEditingOverlayCallback { horizontalMargins, voiceNum ->
            if (voiceNum !in 1..4){
                throw IllegalArgumentException("Only voices 1 to 4 can exist!")
            }
            val voiceGridOverlay = voiceGridOverlays[voiceNum]
            if (voiceGridOverlay != null) {
                voiceGridOverlay.horizontalMargins = horizontalMargins
                if (voiceGridOverlay.width > 0) {
                    voiceGridOverlay.createOverlay()
                }
            }
        }
        barVisLayout = newBarVisLayout
    }

    private fun addBarVisLayout(bar: Bar) : BarVisLayout{

        val barVisLayout = BarVisLayout(context, barHeight, bar)
        barVisLayout.id = ViewGroup.generateViewId()
        barVisLayout.tag = "barVisLayout"
        barVisLayout.layoutParams = ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        this.addView(barVisLayout)

        return barVisLayout
    }

    private fun addBarEditingOverlayLayout() : BarEditingOverlayLayout{

        val barEditingOverlayLayout = BarEditingOverlayLayout(context, barHeight)
        barEditingOverlayLayout.id = generateViewId()
        barEditingOverlayLayout.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (barHeight * 1.75).toInt())
        this.addView(barEditingOverlayLayout)

        barEditingOverlayLayout.doOnLayout {
            val constraintSet = ConstraintSet()
            constraintSet.clone(this)
            constraintSet.connect(barEditingOverlayLayout.id, ConstraintSet.TOP, this.id, ConstraintSet.TOP)
            constraintSet.connect(barEditingOverlayLayout.id, ConstraintSet.BOTTOM, this.id, ConstraintSet.BOTTOM)
            constraintSet.applyTo(this)
        }

        return barEditingOverlayLayout
    }

    /**
     * Change visibility of voice grids to only show the one with the given [voiceNum].
     * If [editingMode] is [MainActivity.EditingMode.DELETE], a grid is only shown for voices that
     * exist in a bar.
     * For [MainActivity.EditingMode.ADD], a grid is always shown, so new bar voices can be created.
     */
    fun changeVoiceGrid(voiceNum: Int, editingMode: MainActivity.EditingMode){
        if (voiceNum !in 1..4){
            throw IllegalArgumentException("Only voices 1 to 4 can exist!")
        }

        voiceGridOverlays.values.forEach {
            it.visibility = INVISIBLE
        }

        when (editingMode){
            MainActivity.EditingMode.ADD -> {
                voiceGridOverlays[voiceNum]?.visibility = VISIBLE
            }
            MainActivity.EditingMode.DELETE -> {
                if (bar.voices.containsKey(voiceNum)){
                    voiceGridOverlays[voiceNum]?.visibility = VISIBLE
                }
            }
        }

        activeVoiceOverlayNum = voiceNum
    }

    /**
     * Changes the displayed bar to the next one of the score. If none exists, one with
     * the same time signature as the current is appended.
     */
    fun nextBar(editingMode: MainActivity.EditingMode){
        // Enable previous bar button.
        if (previousButtonDisabled) {
            prevBarButton.isClickable = true
            previousButtonDisabled = false
        }

        if (barIdx == bars.size - 1){
            val newBar = Bar.makeEmpty(barIdx + 2, bar.timeSignature)
            bars.add(newBar)
        }

        barIdx++
        updateOverlays(bars[barIdx], editingMode)
        bar = bars[barIdx]
    }

    /**
     * Changes the displayed bar to the previous one of the score. If the current bar only contains
     * rests, is the last of the score, and has the same time signature as the previous, it is deleted.
     * Disables previous bar button if going to the first bar.
     *
     * @throws IllegalStateException When called while the current bar is the first of the score.
     */
    fun previousBar(editingMode: MainActivity.EditingMode){
        if (previousButtonDisabled){
            prevBarButton.isClickable = false
            return
        }

        if (barIdx <= 0){
            throw IllegalStateException("Previous bar button should be disabled!")
        }

        barIdx--
        if (barIdx == 0){
            prevBarButton.isClickable = false
            previousButtonDisabled = true
        }

        val previousBar = bars[barIdx]
        if (bar.isBarOfRests() && bar.timeSignature.equals(previousBar.timeSignature) && bar == bars.last()){
            bars.removeAt(barIdx + 1)
        }
        updateOverlays(previousBar, editingMode)
        bar = previousBar
    }

    /**
     * Updates [voiceGridOverlays] visibility to match the musical content of the current bar.
     * Grid content is automatically updated via [barVisLayout] callbacks.
     */
    private fun updateOverlays(nextBar: Bar, editingMode: MainActivity.EditingMode){
        voiceGridOverlays.forEach{
            it.value.visibility =
                    if (it.key == activeVoiceOverlayNum) {
                        when (editingMode){
                            MainActivity.EditingMode.ADD -> VISIBLE
                            MainActivity.EditingMode.DELETE -> {
                                if (nextBar.voices.containsKey(it.key)){
                                    VISIBLE
                                }
                                else INVISIBLE
                            }
                        }
                    }
                    else INVISIBLE
        }
    }

    /**
     * Changes the time signature of the current bar.
     * If its length increased, rests are appended to all voices to fill the bar.
     * If it decreased, the bar is sliced into multiples, so new bars will be inserted after the current.
     * In case of length increase or length remaining the same, the time signatures of following bars which
     * have the same time signature as the current one are adapted as well.
     */
    fun changeCurrentBarTimeSignature(newTimeSignature: TimeSignature){
        val currentBarTimeSignature = bar.timeSignature

        if (!newTimeSignature.equals(currentBarTimeSignature)){
            // Deal with larger new time signature.
            if (newTimeSignature.units > bar.timeSignature.units){
                var nextBar = bar
                var nextBarIdx = barIdx
                while (nextBar.timeSignature.equals(currentBarTimeSignature)){
                    nextBar.changeTimeSignatureToLarger(newTimeSignature)
                    nextBarIdx++
                    if (nextBarIdx == bars.size) break
                    nextBar = bars[nextBarIdx]
                }
            }
            // Deal with smaller new time signature.
            else if (newTimeSignature.units < bar.timeSignature.units) {

                // Adapt current bar and receive voice intervals of bars to be created.
                val newBarsVoiceIntervals = bar.changeTimeSignatureToSmaller(newTimeSignature)
                if (newBarsVoiceIntervals != null){
                    val arbitraryVoice = newBarsVoiceIntervals.toList().firstOrNull()
                        ?: throw IllegalStateException("newBarsVoiceIntervals should be null instead of returning empty map!")

                    // create the new bars
                    // if one only contains rests, a new bar with one voice of maximum rests will be created
                    // if it doesn't, voices which only contain rests will be ignored when creating new bars
                    for (newBarIdx in arbitraryVoice.second.indices){
                        val newBarVoiceIntervals : MutableMap<Int, MutableList<RhythmicInterval>> = mutableMapOf()
                        val onlyRestVoiceNums = mutableListOf<Int>()
                        var onlyHasRests = true
                        for (voiceNum in 1..4){
                            if (newBarsVoiceIntervals.containsKey(voiceNum)){
                                val currentBarIntervalsOfVoice = newBarsVoiceIntervals[voiceNum]?.get(newBarIdx)
                                    ?: throw IllegalStateException("newBarsVoiceIntervals contains no element at this barIdx!")
                                if (currentBarIntervalsOfVoice.isNotEmpty()){
                                    var isListOfRests = true
                                    for (interval in currentBarIntervalsOfVoice){
                                        if (!interval.isRest){
                                            isListOfRests = false
                                            onlyHasRests = false
                                            break
                                        }
                                    }
                                    if (isListOfRests) onlyRestVoiceNums.add(voiceNum)
                                    newBarVoiceIntervals[voiceNum] = currentBarIntervalsOfVoice
                                }
                            }
                        }

                        if (!onlyHasRests){
                            onlyRestVoiceNums.forEach { newBarVoiceIntervals.remove(it) }
                        }
                        else {
                            newBarVoiceIntervals.clear()
                        }

                        if (newBarVoiceIntervals.isEmpty()){
                            bars.add(barIdx + newBarIdx + 1, Bar.makeEmpty(bar.barNr + newBarIdx + 1, newTimeSignature))
                        }
                        else {
                            bars.add(barIdx + newBarIdx + 1, Bar(bar.barNr + newBarIdx + 1, newTimeSignature, newBarVoiceIntervals))
                        }
                    }

                    // Increase bar numbers for bars after inserted.
                    for (otherBar in bars.subList(barIdx + 1 + arbitraryVoice.second.size, bars.size)){
                        otherBar.barNr += arbitraryVoice.second.size
                    }
                }
            }
            // Deal with new time signature of same length.
            else {
                var nextBar = bar
                var nextBarIdx = barIdx
                while (nextBar.timeSignature.equals(currentBarTimeSignature)){
                    nextBar.timeSignature = newTimeSignature
                    for (voice in nextBar.voices.values){
                        voice.initializeSubGroups()
                    }
                    nextBarIdx++
                    if (nextBarIdx == bars.size) break
                    nextBar = bars[nextBarIdx]
                }
            }

            // Update the displayed content of current bar.
            val currentBarVisLayout = barVisLayout
                    ?: throw IllegalStateException("Can't update bar visualization because barVisLayout is null!")
            currentBarVisLayout.visualizeBar()
        }
    }

    /**
     * Changes the currently displayed bar to the one specified by [barNr]. [editingMode] is needed
     * to use [updateOverlays] accordingly.
     * Previous bar button is disabled when going to first bar, enabled otherwise.
     */
    fun goToBar(barNr: Int, editingMode: MainActivity.EditingMode){
        if (barNr > bars.size){
            throw IllegalArgumentException("barNr exceeds bar list!")
        }
        barIdx = barNr - 1
        bar = bars[barIdx]
        if (bar.barNr != barNr){
            throw IllegalStateException("Bar at idx derived from barNr does not have this bar number!")
        }
        if (barIdx == 0){
            prevBarButton.isClickable = false
            previousButtonDisabled = true
        }
        else {
            prevBarButton.isClickable = true
            previousButtonDisabled = false
        }
        updateOverlays(bar, editingMode)
    }
}