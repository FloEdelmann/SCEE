package de.westnordost.streetcomplete.quests.amenity_indoor

import de.westnordost.osmfeatures.Feature
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolygonsGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.*
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.quests.YesNoQuestForm
import de.westnordost.streetcomplete.util.ktx.toYesNo
import de.westnordost.streetcomplete.util.math.LatLonRaster
import de.westnordost.streetcomplete.util.math.contains
import de.westnordost.streetcomplete.util.math.isCompletelyInside
import de.westnordost.streetcomplete.util.math.isInMultipolygon

class AddIsAmenityIndoor(private val getFeature: (tags: Map<String, String>) -> Feature?) :
    OsmElementQuestType<Boolean> {

    private val nodesFilter by lazy { """
        nodes with
          (
            emergency ~ defibrillator|fire_extinguisher
            or amenity ~ atm|telephone|parcel_locker|luggage_locker|locker|clock|post_box|public_bookcase|give_box|ticket_validator|vending_machine
          )
          and access !~ private|no
          and !indoor and !location and !level and !level:ref
    """.toElementFilterExpression() }

    /* We only want survey nodes within building outlines. */
    // exclude building=roof, see https://github.com/streetcomplete/StreetComplete/issues/5333
    private val buildingFilter by lazy { """
        ways, relations with 
            building
            and building != roof 
    """.toElementFilterExpression() }

    override val changesetComment = "Determine whether amenities are inside buildings"
    override val wikiLink = "Key:indoor"
    override val icon = R.drawable.ic_quest_building_inside
    override val achievements = listOf(CITIZEN)

    override fun getTitle(tags: Map<String, String>) = R.string.quest_is_amenity_inside_title

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> {
        val bbox = mapData.boundingBox ?: return listOf()
        val nodes = mapData.nodes.filter {
            nodesFilter.matches(it) && hasAnyName(it.tags)
        }
        val buildings = mapData.filter { buildingFilter.matches(it) }.toMutableList()

        val buildingGeometriesById = buildings.associate {
            it.id to mapData.getGeometry(it.type, it.id) as? ElementPolygonsGeometry
        }

        val nodesPositions = LatLonRaster(bbox, 0.0005)
        for (node in nodes) {
            nodesPositions.insert(node.position)
        }

        buildings.removeAll { building ->
            val buildingBounds = buildingGeometriesById[building.id]?.getBounds()
            (buildingBounds == null || !buildingBounds.isCompletelyInside(bbox) || nodesPositions.getAll(buildingBounds).count() == 0)
        }

        // Reduce all matching nodes to nodes within building outlines
        val nodesInBuildings = nodes.filter {
            buildings.any { building ->
                val buildingGeometry = buildingGeometriesById[building.id]

                if (buildingGeometry != null  && buildingGeometry.getBounds().contains(it.position)) {
                    it.position.isInMultipolygon(buildingGeometry.polygons)
                } else {
                    false
                }
            }
        }

        return nodesInBuildings
    }

    override fun isApplicableTo(element: Element) =
        if (!nodesFilter.matches(element) || !hasAnyName(element.tags)) false else null

    private fun hasAnyName(tags: Map<String, String>) = getFeature(tags) != null

    override fun getHighlightedElements(element: Element, getMapData: () -> MapDataWithGeometry): Sequence<Element> {
        /* put markers for objects that are exactly the same as for which this quest is asking for
           e.g. it's a ticket validator? -> display other ticket validators. Etc. */
        val feature = getFeature(element.tags) ?: return emptySequence()

        return getMapData().filter { it.tags.containsAll(feature.tags) }.asSequence()
    }

    override fun createForm() = YesNoQuestForm()

    override fun applyAnswerTo(answer: Boolean, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        tags["indoor"] = answer.toYesNo()
    }
}

private fun <X, Y> Map<X, Y>.containsAll(other: Map<X, Y>) = other.all { this[it.key] == it.value }
