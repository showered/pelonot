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
