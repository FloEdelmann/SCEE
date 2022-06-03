package de.westnordost.streetcomplete.quests.roof_shape

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import de.westnordost.countryboundaries.CountryBoundaries
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.meta.CountryInfos
import de.westnordost.streetcomplete.data.meta.getByLocation
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.Tags
import de.westnordost.streetcomplete.data.user.achievements.QuestTypeAchievement.BUILDING
import de.westnordost.streetcomplete.quests.numberSelectionDialog
import de.westnordost.streetcomplete.quests.questPrefix
import java.util.concurrent.FutureTask

class AddRoofShape(
    private val countryInfos: CountryInfos,
    private val countryBoundariesFuture: FutureTask<CountryBoundaries>,
    private val prefs: SharedPreferences,
) : OsmElementQuestType<RoofShape> {

    private val filter by lazy { """
        ways, relations with (building:levels or roof:levels)
          and !roof:shape and !3dr:type and !3dr:roof
          and building
          and building !~ no|construction
    """.toElementFilterExpression() }

    override val changesetComment = "Add roof shapes"
    override val wikiLink = "Key:roof:shape"
    override val icon = R.drawable.ic_quest_roof_shape
    override val defaultDisabledMessage = R.string.default_disabled_msg_roofShape
    override val questTypeAchievements = listOf(BUILDING)

    override fun getTitle(tags: Map<String, String>) = R.string.quest_roofShape_title

    override fun createForm() = AddRoofShapeForm()

    override fun getApplicableElements(mapData: MapDataWithGeometry) =
        mapData.filter { element ->
            filter.matches(element) && (
                element.tags["roof:levels"]?.toFloatOrNull() ?: 0f > 0f
                || roofsAreUsuallyFlatAt(element, mapData) == false
            ) && (element.tags["building:levels"]?.toIntOrNull() ?: 0) +
                (element.tags["roof:levels"]?.toIntOrNull() ?: 0) <= prefs.getInt(questPrefix(prefs) + PREF_ROOF_SHAPE_MAX_LEVELS, 99)
        }

    override fun isApplicableTo(element: Element): Boolean? {
        if (!filter.matches(element)) return false
        /* if it has 0 roof levels, or the roof levels aren't specified,
           the quest should only be shown in certain countries. But whether
           the element is in a certain country cannot be ascertained without the element's geometry */
        if (element.tags["roof:levels"]?.toFloatOrNull() ?: 0f == 0f) return null
        return true
    }

    private fun roofsAreUsuallyFlatAt(element: Element, mapData: MapDataWithGeometry): Boolean? {
        val center = mapData.getGeometry(element.type, element.id)?.center ?: return null
        return countryInfos.getByLocation(
            countryBoundariesFuture.get(),
            center.longitude,
            center.latitude,
        ).roofsAreUsuallyFlat
    }

    override fun applyAnswerTo(answer: RoofShape, tags: Tags, timestampEdited: Long) {
        tags["roof:shape"] = answer.osmValue
    }

    override val hasQuestSettings = true

    override fun getQuestSettingsDialog(context: Context) = numberSelectionDialog(
        context, prefs, questPrefix(prefs) + PREF_ROOF_SHAPE_MAX_LEVELS, 99, R.string.quest_settings_max_roof_levels
    )

}

private const val PREF_ROOF_SHAPE_MAX_LEVELS = "quest_roof_shape_max_levels"
