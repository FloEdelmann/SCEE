package de.westnordost.streetcomplete.quests.osmose

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.databinding.QuestOsmoseExternalBinding
import de.westnordost.streetcomplete.quests.AbstractQuestAnswerFragment
import de.westnordost.streetcomplete.quests.AnswerItem

class OsmoseForm(private val db: OsmoseDao) : AbstractQuestAnswerFragment<OsmoseAnswer>() {

    var issue: OsmoseIssue? = null

    override val buttonPanelAnswers = mutableListOf<AnswerItem>()

    override val contentLayoutResId = R.layout.quest_osmose_external
    private val binding by contentViewBinding(QuestOsmoseExternalBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val element = osmElement ?: return
        issue = db.get(ElementKey(element.type, element.id))
        val text = issue?.let {
            if (it.subtitle.isBlank())
                it.title
            else
                it.title + ": \n" + it.subtitle
        }
        binding.description.text =
            if (text == null) resources.getString(R.string.quest_external_osmose_not_found)
            else resources.getString(R.string.quest_osmose_message_for_element, issue?.item, text)
        if (issue?.subtitle?.startsWith("Concerns tag:") == true) {
            val tag = issue?.subtitle?.substringAfter("Concerns tag: `")?.substringBefore("`") ?: return
            buttonPanelAnswers.add(
                AnswerItem(R.string.quest_osmose_modify_tag) {
                    activity?.let {
                        val inputEditTextField = EditText(it)
                        inputEditTextField.setText(tag.substringAfter("="))
                        AlertDialog.Builder(it)
                            .setTitle(R.string.quest_osmose_set_value)
                            .setMessage(tag.substringBefore("=") + " =")
                            .setView(inputEditTextField)
                            .setPositiveButton(android.R.string.ok) {_,_ ->
                                val newValue = inputEditTextField.text.toString()
                                if (newValue.isNotBlank())
                                    applyAnswer(AdjustTagAnswer(
                                        issue?.uuid ?: "",
                                        tag.substringBefore("="),
                                        newValue.trim()
                                    ))
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
            )
        }
        if (issue != null)
            // TODO: dialog that warns that there is no undo
            buttonPanelAnswers.add(AnswerItem(R.string.quest_osmose_false_positive) {
                db.setAsFalsePositive(issue?.uuid ?: "", questKey)
                // quest removed from db, tempHide to make it disappear
                tempSkipQuest()
            } )
        updateButtonPanel()
    }

}

sealed interface OsmoseAnswer
class AdjustTagAnswer(val uuid: String, val tag: String, val newValue: String) : OsmoseAnswer
