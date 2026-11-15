// SkillIcon: vanilla's framed skill icon (the engine's own, found at load by its readable methods), at a level given
// or at a person's level read live; made at its final size and made again when that changes.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.characters.SkillSpecAPI
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.layout.VanillaElementEngine
import org.starficz.starui.modifier.anchor
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import org.starficz.starui.vanilla.VanillaClasses

/** The vanilla icon a SkillIcon holds now. */
private fun iconOf(panel: CustomPanelAPI): UIComponentAPI =
    (StarUIPanelPlugin.getAttached(panel)!!.layoutEngine as VanillaElementEngine).current!!

fun registerSkillIconTests() {
    StarUITestSuite.group("skill-icon") {
        expect(VanillaClasses.skillIconConstructor != null, "vanilla's skill icon and its constructor are found at load")
        val person = Global.getFactory().createPerson()
        val big = UIState(false)
        val made = linkedMapOf<String, CustomPanelAPI>()
        lateinit var live: CustomPanelAPI
        lateinit var sized: CustomPanelAPI
        root.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)), spacing = 10f) {
            Row(spacing = 6f) {
                made["not learned"] = SkillIcon("helmsmanship", learned = false)
                made["learned"] = SkillIcon("helmsmanship")
                made["elite"] = SkillIcon("helmsmanship", elite = true)
                made["thin"] = SkillIcon("target_analysis", thinBorder = true)
                made["64 elite"] = SkillIcon("point_defense", Size.exact(64f, 64f), elite = true)
            }
            Row(spacing = 6f) {
                live = SkillIcon("helmsmanship", person)
                val side = if (big.value) 56f else 40f
                sized = SkillIcon("target_analysis", Size.exact(side, side), elite = true)
            }
        }
        frame(); frame()
        for ((name, panel) in made) {
            val icon = iconOf(panel)
            expect(icon.javaClass == VanillaClasses.skillIconClass, "$name holds vanilla's skill icon")
            near(icon.position.width, panel.position.width, "$name's icon fills its panel")
        }
        near(made["learned"]!!.position.width, 40f, "40 x 40 by default, vanilla's skill panel size")
        near(made["64 elite"]!!.position.height, 64f, "an exact size is kept")
        val spec = iconOf(made["learned"]!!).invoke("getSkill") as SkillSpecAPI
        expect(spec.id == "helmsmanship", "the icon shows the skill asked for (${spec.id})")
        screenshot("states")

        // the person overload reads the person's level every frame: no remake when it changes
        val liveIcon = iconOf(live)
        expect(liveIcon.invoke("getStats") === person.stats, "the person overload reads that person's stats")
        person.stats.setSkillLevel("helmsmanship", 2f)
        frame(); frame()
        expect(iconOf(live) === liveIcon, "a level change shows on the same icon, without making it again")
        screenshot("person-elite")

        // it builds its sprites for its first size: a new size makes a new icon at that size
        val before = iconOf(sized)
        big.value = true
        frame(); frame()
        expect(iconOf(sized) !== before, "a new size makes the icon again")
        near(iconOf(sized).position.width, 56f, "at the new size (${iconOf(sized).position.width})")
    }
}
