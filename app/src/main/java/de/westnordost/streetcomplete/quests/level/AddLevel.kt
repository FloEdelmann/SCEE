package de.westnordost.streetcomplete.quests.level

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolygonsGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.Tags
import de.westnordost.streetcomplete.data.user.achievements.QuestTypeAchievement.CITIZEN
import de.westnordost.streetcomplete.osm.isShopExpressionFragment
import de.westnordost.streetcomplete.quests.questPrefix
import de.westnordost.streetcomplete.quests.showRestartToast
import de.westnordost.streetcomplete.util.math.contains
import de.westnordost.streetcomplete.util.math.isInMultipolygon

class AddLevel(
    private val prefs: SharedPreferences
) : OsmElementQuestType<String> {

    /* including any kind of public transport station because even really large bus stations feel
     * like small airport terminals, like Mo Chit 2 in Bangkok*/
    private val mallFilter by lazy { """
        ways, relations with
         shop = mall
         or aeroway = terminal
         or railway = station
         or amenity = bus_station
         or public_transport = station
         ${if (prefs.getBoolean(questPrefix(prefs) + PREF_MORE_LEVELS, false)) "or (building and building:levels != 1)" else ""}
    """.toElementFilterExpression() }

    private val thingsWithLevelOrDoctorsFilter by lazy { """
        nodes, ways, relations with level
        ${if (prefs.getBoolean(questPrefix(prefs) + PREF_MORE_LEVELS, false)) """
        and (
          amenity ~ doctors|dentist
          or healthcare ~ psychotherapist|physiotherapist
        ) """
        else ""}
    """.toElementFilterExpression() }

    /* only nodes because ways/relations are not likely to be floating around freely in a mall
    *  outline */
    private val filter get() = if (prefs.getBoolean(questPrefix(prefs) + PREF_MORE_LEVELS, false))
        shopsAndMoreFilter
    else
        shopFilter

    private val shopsAndMoreFilter by lazy { """
        nodes with
         (
           (shop and shop !~ no|vacant|mall)
           or craft
           or amenity
           or leisure
           or office
           or tourism
         )
         and !level
    """.toElementFilterExpression()}

    private val shopFilter by lazy { """
        nodes with
         (${isShopExpressionFragment()})
         and !level and (name or brand)
    """.toElementFilterExpression() }

    override val changesetComment = "Add level to elements"
    override val wikiLink = "Key:level"
    override val icon = R.drawable.ic_quest_level
    /* disabled because in a mall with multiple levels, if there are nodes with no level defined,
    *  it really makes no sense to tag something as vacant if the level is not known. Instead, if
    *  the user cannot find the place on any level in the mall, delete the element completely. */
    override val isReplaceShopEnabled = false
    override val isDeleteElementEnabled = true
    override val questTypeAchievements = listOf(CITIZEN)

    override fun getTitle(tags: Map<String, String>) = R.string.quest_level_title2

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> {
        // get all shops that have no level tagged
        val shopsWithoutLevel = mapData
            .filter { filter.matches(it)}
            .toMutableList()
        if (shopsWithoutLevel.isEmpty()) return emptyList()

        val result = mutableListOf<Element>()
        if (prefs.getBoolean(questPrefix(prefs) + PREF_MORE_LEVELS, false)) {
            // add doctors, independent of the building they're in
            // and remove them from shops without level
            shopsWithoutLevel.removeAll {
                if (it.isDoctor()) {
                    result.add(it)
                    true
                } else
                    false
            }
        }

        // get geometry of all malls (or buildings) in the area
        val mallGeometries = mapData
            .filter { mallFilter.matches(it) }
            .mapNotNull { mapData.getGeometry(it.type, it.id) as? ElementPolygonsGeometry }
        if (mallGeometries.isEmpty()) return result

        // get all shops that have level tagged or are doctors
        val thingsWithLevel = mapData.filter { thingsWithLevelOrDoctorsFilter.matches(it) }
        if (thingsWithLevel.isEmpty()) return result

        // with this, find malls that contain shops that have different levels tagged
        val multiLevelMallGeometries = mallGeometries.filter { mallGeometry ->
            var level: String? = null
            for (shop in thingsWithLevel) {
                val pos = mapData.getGeometry(shop.type, shop.id)?.center ?: continue
                if (!mallGeometry.getBounds().contains(pos)) continue
                if (!pos.isInMultipolygon(mallGeometry.polygons)) continue

                if (shop.tags.containsKey("level")) {
                    if (level != null) {
                        if (level != shop.tags["level"]) return@filter true
                    } else {
                        level = shop.tags["level"]
                    }
                }
            }
            return@filter false
        }
        if (multiLevelMallGeometries.isEmpty()) return result

        for (mallGeometry in multiLevelMallGeometries) {
            val it = shopsWithoutLevel.iterator()
            while (it.hasNext()) {
                val shop = it.next()
                val pos = mapData.getGeometry(shop.type, shop.id)?.center ?: continue
                if (!mallGeometry.getBounds().contains(pos)) continue
                if (!pos.isInMultipolygon(mallGeometry.polygons)) continue

                result.add(shop)
                it.remove() // shop can only be in one mall
            }
        }
        return result
    }

    override fun isApplicableTo(element: Element): Boolean? {
        if (!filter.matches(element)) return false
        // doctors are frequently at non-ground level
        if (element.isDoctor() && prefs.getBoolean(questPrefix(prefs) + PREF_MORE_LEVELS, false) && !element.tags.containsKey("level")) return true
        // for shops with no level, we actually need to look at geometry in order to find if it is
        // contained within any multi-level mall
        return null
    }

    private fun Element.isDoctor() = tags["amenity"] == "doctors" || tags["amenity"] == "dentist" || tags["healthcare"] in listOf("psychotherapist", "physiotherapist")

    override fun createForm() = AddLevelForm()

    override fun applyAnswerTo(answer: String, tags: Tags, timestampEdited: Long) {
        tags["level"] = answer
    }

    override val hasQuestSettings = true

    override fun getQuestSettingsDialog(context: Context): AlertDialog {
        return AlertDialog.Builder(context)
            .setTitle(R.string.quest_settings_level_title)
            .setNegativeButton(android.R.string.cancel, null)
            .setItems(R.array.pref_quest_settings_levels) { _, i ->
                prefs.edit().putBoolean(questPrefix(prefs) + PREF_MORE_LEVELS, i == 1).apply()
                showRestartToast(context)
            }
            .create()
    }

}

private const val PREF_MORE_LEVELS = "prefs_more_levels"
