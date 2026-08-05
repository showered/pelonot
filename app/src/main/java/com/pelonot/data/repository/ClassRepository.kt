package com.pelonot.data.repository

import com.pelonot.data.local.dao.ClassTemplateDao
import com.pelonot.data.local.entity.ClassTemplateEntity
import com.pelonot.domain.model.Interval
import com.pelonot.domain.model.IntervalParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A class template with its intervals already decoded. */
data class ClassPlan(
    val template: ClassTemplateEntity,
    val intervals: List<Interval>
) {
    val id: String get() = template.id
    val title: String get() = template.title
    val category: String get() = template.category
    val durationSec: Int get() = template.durationSec

    /**
     * What the ride is for, or null when this class has not been given one
     * (PLAN 23.2.7).
     *
     * Null rather than the entity's empty string, because a caller drawing this
     * has to decide whether to draw anything at all, and `""` is a value that
     * every naive `if (description != null)` gets wrong. The column stays empty
     * for the reason on the entity; the *UI's* question is different.
     */
    val description: String? get() = template.description.takeIf { it.isNotBlank() }

    /** True when the template's `intervals_json` could not be decoded. */
    val isMalformed: Boolean get() = intervals.isEmpty()
}

class ClassRepository(
    private val classTemplateDao: ClassTemplateDao
) {

    val allTemplates: Flow<List<ClassTemplateEntity>> = classTemplateDao.getAllTemplates()

    /** Every template with intervals parsed once, up front. */
    val allPlans: Flow<List<ClassPlan>> = allTemplates.map { templates ->
        templates.map { it.toPlan() }
    }

    /** The distinct categories present in the library, for filter chips. */
    val categories: Flow<List<String>> = allTemplates.map { templates ->
        templates.map { it.category }.distinct().sorted()
    }

    suspend fun getPlan(id: String): ClassPlan? =
        classTemplateDao.getTemplateById(id)?.toPlan()

    private fun ClassTemplateEntity.toPlan() =
        ClassPlan(template = this, intervals = IntervalParser.parseOrEmpty(intervalsJson))
}
