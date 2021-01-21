package com.example.sheetmusicapp

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.doOnLayout
import com.example.sheetmusicapp.parser.ScoreDeserializer
import com.example.sheetmusicapp.parser.ScoreSerializer
import com.example.sheetmusicapp.scoreModel.*
import com.example.sheetmusicapp.ui.BarVisLayout
import com.example.sheetmusicapp.ui.EditableBarLayout
import com.google.android.material.snackbar.Snackbar
import com.google.gson.GsonBuilder

const val CREATE_FILE = 1
const val PICK_FILE = 2


class MainActivity : AppCompatActivity() {
    var parser = GsonBuilder()
    var editableBarLayout : EditableBarLayout? = null

    private fun initParser() {
        parser.registerTypeAdapter(Score::class.java, ScoreSerializer())
        parser.registerTypeAdapter(Score::class.java, ScoreDeserializer())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                // Perform operations on the document using its URI.
                println(uri)
                val exampleScore = Score.makeEmpty(bars = 32, timeSignature =  TimeSignature(4, 4))
                val json = parser.setPrettyPrinting().create().toJson(exampleScore)
                val file = contentResolver.openOutputStream(uri)
                file?.write(json.toByteArray())
            }
        }
        if (requestCode == PICK_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                println(uri)
                // Perform operations on the document using its URI.
                val file = contentResolver.openInputStream(uri)
                val jsonRaw = file?.readBytes()
                var json = jsonRaw?.let { String(it) }
                val test = parser.create().fromJson(json, Score::class.java)
                if (test == null) {
                    Snackbar.make(
                            findViewById(R.id.main),
                            "Wrong file format",
                            Snackbar.LENGTH_SHORT
                    ).show()
                }
//                println(test)
//                val mainConstraintLayout = findViewById<ConstraintLayout>(R.id.main)
//                mainConstraintLayout.doOnLayout {
//                    scaleBarLineStrokeWidth()
//                    visualizeBar(test.barList[0])
//                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initButtonGroups() {
        val openFileButton: Button = findViewById(R.id.button)
        openFileButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            startActivityForResult(intent, PICK_FILE)
        }
        val saveFileButton: Button = findViewById(R.id.button2)
        saveFileButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "sheet.json")
            }
            startActivityForResult(intent, CREATE_FILE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initParser()


        // val exampleScore = Score.makeEmpty(bars = 32, timeSignature =  TimeSignature(4, 4))
        val exampleBar = Bar.makeEmpty(1, TimeSignature(4, 4))

        exampleBar.addNote(1, RhythmicLength(BasicRhythmicLength.EIGHTH), NoteHeadType.ELLIPTIC, 11, 0)
        exampleBar.addNote(1, RhythmicLength(BasicRhythmicLength.EIGHTH), NoteHeadType.ELLIPTIC, 11, 1)
        exampleBar.addNote(1, RhythmicLength(BasicRhythmicLength.EIGHTH), NoteHeadType.ELLIPTIC, 11, 2)
        exampleBar.addNote(1, RhythmicLength(BasicRhythmicLength.EIGHTH), NoteHeadType.ELLIPTIC, 11, 3)
        exampleBar.addNote(1, RhythmicLength(BasicRhythmicLength.EIGHTH), NoteHeadType.ELLIPTIC, 11, 4)
        exampleBar.addNote(1, RhythmicLength(BasicRhythmicLength.EIGHTH), NoteHeadType.ELLIPTIC, 11, 5)
        exampleBar.addNote(1, RhythmicLength(BasicRhythmicLength.EIGHTH), NoteHeadType.ELLIPTIC, 11, 6)
        exampleBar.addNote(1, RhythmicLength(BasicRhythmicLength.EIGHTH), NoteHeadType.ELLIPTIC, 11, 7)

        exampleBar.addNote(2, RhythmicLength(BasicRhythmicLength.QUARTER), NoteHeadType.ELLIPTIC, 3, 0)
        exampleBar.addNote(2, RhythmicLength(BasicRhythmicLength.QUARTER), NoteHeadType.ELLIPTIC, 7, 1)
        exampleBar.addNote(2, RhythmicLength(BasicRhythmicLength.QUARTER), NoteHeadType.ELLIPTIC, 3, 2)
        exampleBar.addNote(2, RhythmicLength(BasicRhythmicLength.QUARTER), NoteHeadType.ELLIPTIC, 7, 3)
        // exampleBar.addRest(1, RhythmicLength(BasicRhythmicLength.QUARTER, LengthModifier.DOTTED), 0)

        setContentView(R.layout.activity_main)

        val mainConstraintLayout = findViewById<ConstraintLayout>(R.id.main)
        mainConstraintLayout.doOnLayout {
            editableBarLayout = addEditableBarLayout(exampleBar)
        }
        initButtonGroups()
    }

    /**
     * Adds reusable bar layout component and binds the given [bar] to it. It will be displayed when
     * the returned layout it laid out.
     *
     * @throws IllegalStateException When height or width of this activity's main layout is 0, appearing
     * as it has not been laid out yet.
     */
    private fun addEditableBarLayout(bar: Bar) : EditableBarLayout{

        val mainLayout = findViewById<ConstraintLayout>(R.id.main)
        if (mainLayout.height == 0 || mainLayout.width == 0){
            throw IllegalStateException("'main' layout has not been laid out yet!" )
        }
        val horizontalBarMargin = (mainLayout.width * 0.15).toInt()
        val width = (mainLayout.width * 0.7).toInt()

        val editableBarLayout = EditableBarLayout(this, 1 / 3.0, bar)
        editableBarLayout.id = ViewGroup.generateViewId()
        editableBarLayout.tag = "editableBarLayout"
        editableBarLayout.layoutParams = ViewGroup.LayoutParams(width, mainLayout.height)
        mainLayout.addView(editableBarLayout)

        val constraintSet = ConstraintSet()
        constraintSet.clone(mainLayout)
        constraintSet.connect(editableBarLayout.id, ConstraintSet.LEFT, R.id.main, ConstraintSet.LEFT, horizontalBarMargin)
        constraintSet.connect(editableBarLayout.id, ConstraintSet.RIGHT, R.id.main, ConstraintSet.RIGHT, horizontalBarMargin)
        constraintSet.applyTo(mainLayout)

        return editableBarLayout
    }

    fun handleVoiceChange(view: View){
        val voiceNumButton = findViewById<Button>(R.id.voiceNumButton)
        val currentNum = voiceNumButton.text.toString().toInt()
        val newNum = currentNum % 4 + 1
        voiceNumButton.text = newNum.toString()
        editableBarLayout?.changeVisibleGrid(newNum)
    }

}