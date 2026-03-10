package com.droidrun.portal.ui.taskprompt

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.droidrun.portal.R
import com.droidrun.portal.databinding.DialogTaskPromptModelPickerBinding
import com.droidrun.portal.databinding.ItemTaskPromptModelOptionBinding
import com.droidrun.portal.databinding.ViewTaskPromptCardBinding
import com.droidrun.portal.taskprompt.PortalCloudClient
import com.droidrun.portal.taskprompt.PortalModelOption
import com.droidrun.portal.taskprompt.PortalTaskDraft
import com.droidrun.portal.taskprompt.PortalTaskSettings
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class TaskPromptCardController(
    private val context: Context,
    layoutInflater: LayoutInflater,
    private val onSubmit: (PortalTaskDraft) -> Unit,
    private val onCancelTask: () -> Unit,
    private val onOpenTaskDetails: (String) -> Unit,
    private val onOpenTaskHistory: () -> Unit,
) {
    data class TaskStateViewModel(
        val taskId: String,
        val statusLabel: String,
        val statusKind: StatusKind,
        val promptPreview: String? = null,
        val summary: String? = null,
        val isClickable: Boolean = false,
        val isBlocking: Boolean = false,
        val canCancel: Boolean = false,
        val cancelInFlight: Boolean = false,
    )

    enum class StatusKind {
        INFO,
        SUCCESS,
        ERROR,
    }

    private val binding = ViewTaskPromptCardBinding.inflate(layoutInflater)
    private val rootView: View
        get() = binding.root
    private val historyButton: MaterialButton
        get() = binding.taskPromptHistoryButton
    private val promptInputLayout: TextInputLayout
        get() = binding.taskPromptInputLayout
    private val promptInput: TextInputEditText
        get() = binding.taskPromptInput
    private val modelInputLayout: TextInputLayout
        get() = binding.taskPromptModelLayout
    private val modelInput: TextInputEditText
        get() = binding.taskPromptModelInput
    private val reasoningTile: MaterialCardView
        get() = binding.taskPromptReasoningTile
    private val reasoningSwitch: SwitchMaterial
        get() = binding.taskPromptReasoningToggle
    private val visionTile: MaterialCardView
        get() = binding.taskPromptVisionTile
    private val visionSwitch: SwitchMaterial
        get() = binding.taskPromptVisionToggle
    private val advancedHeader: LinearLayout
        get() = binding.taskPromptAdvancedHeader
    private val advancedChevron: ImageView
        get() = binding.taskPromptAdvancedChevron
    private val advancedContent: LinearLayout
        get() = binding.taskPromptAdvancedContent
    private val maxStepsInputLayout: TextInputLayout
        get() = binding.taskPromptMaxStepsLayout
    private val maxStepsInput: TextInputEditText
        get() = binding.taskPromptMaxStepsInput
    private val timeoutInputLayout: TextInputLayout
        get() = binding.taskPromptTimeoutLayout
    private val timeoutInput: TextInputEditText
        get() = binding.taskPromptTimeoutInput
    private val temperatureInputLayout: TextInputLayout
        get() = binding.taskPromptTemperatureLayout
    private val temperatureInput: TextInputEditText
        get() = binding.taskPromptTemperatureInput
    private val statusText: TextView
        get() = binding.taskPromptStatusText
    private val taskStateContainer: View
        get() = binding.taskPromptTaskStateContainer
    private val latestTaskLabel: TextView
        get() = binding.taskPromptLatestLabel
    private val taskStateChip: TextView
        get() = binding.taskPromptTaskStateChip
    private val taskStateDetail: TextView
        get() = binding.taskPromptTaskStateDetail
    private val taskStateSummary: TextView
        get() = binding.taskPromptTaskStateSummary
    private val cancelTaskButton: MaterialButton
        get() = binding.taskPromptCancelButton
    private val submitButton: MaterialButton
        get() = binding.taskPromptSubmitButton
    private val submitProgress: CircularProgressIndicator
        get() = binding.taskPromptSubmitProgress

    private var modelOptions: List<PortalModelOption> = emptyList()
    private var filteredModelOptions: List<PortalModelOption> = emptyList()
    private var currentSettings = PortalTaskSettings()
    private var isModelsLoading = false
    private var isSubmitting = false
    private var canSubmit = false
    private var isFormEnabled = true
    private var isHistoryEnabled = false
    private var isAdvancedExpanded = false
    private var selectedModelId: String? = null
    private var taskState: TaskStateViewModel? = null

    init {
        modelInput.isFocusable = false
        modelInput.isClickable = false
        modelInputLayout.setEndIconOnClickListener { openModelPicker() }
        modelInputLayout.setOnClickListener { openModelPicker() }
        modelInput.setOnClickListener { openModelPicker() }
        historyButton.setOnClickListener { onOpenTaskHistory() }

        advancedHeader.setOnClickListener {
            if (!isFormEnabled) return@setOnClickListener
            isAdvancedExpanded = !isAdvancedExpanded
            advancedContent.visibility = if (isAdvancedExpanded) View.VISIBLE else View.GONE
            advancedChevron.rotation = if (isAdvancedExpanded) 90f else 0f
        }

        reasoningTile.setOnClickListener {
            if (reasoningSwitch.isEnabled) {
                reasoningSwitch.isChecked = !reasoningSwitch.isChecked
            }
        }
        visionTile.setOnClickListener {
            if (visionSwitch.isEnabled) {
                visionSwitch.isChecked = !visionSwitch.isChecked
            }
        }
        reasoningSwitch.setOnCheckedChangeListener { _, _ -> updateToggleTiles() }
        visionSwitch.setOnCheckedChangeListener { _, _ -> updateToggleTiles() }

        cancelTaskButton.setOnClickListener {
            onCancelTask()
        }

        submitButton.setOnClickListener {
            val draft = buildDraft() ?: return@setOnClickListener
            onSubmit(draft)
        }

        updateToggleTiles()
        updateSubmitButtonState()
        applyTaskState(null)
    }

    fun attachTo(container: ViewGroup) {
        if (rootView.parent === container) return
        (rootView.parent as? ViewGroup)?.removeView(rootView)
        container.removeAllViews()
        container.addView(rootView)
        syncSettingTileHeights()
    }

    fun setVisible(visible: Boolean) {
        rootView.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            syncSettingTileHeights()
        }
    }

    fun applySettings(settings: PortalTaskSettings, preservePrompt: Boolean = true) {
        currentSettings = settings
        reasoningSwitch.isChecked = settings.reasoning
        visionSwitch.isChecked = settings.vision
        maxStepsInput.setText(settings.maxSteps.toString())
        timeoutInput.setText(settings.executionTimeout.toString())
        temperatureInput.setText(formatTemperature(settings.temperature))
        if (!preservePrompt) {
            promptInput.setText("")
        }
        selectModel(settings.llmModel)
        updateToggleTiles()
        syncSettingTileHeights()
    }

    fun setModelOptions(options: List<PortalModelOption>) {
        modelOptions = options
        filteredModelOptions = options
        selectModel(selectedModelId ?: currentSettings.llmModel)
        updateSubmitButtonState()
    }

    fun setTaskState(state: TaskStateViewModel?) {
        taskState = state
        applyTaskState(state)
        updateSubmitButtonState()
    }

    fun setModelsLoading(loading: Boolean) {
        isModelsLoading = loading
        updateSubmitButtonState()
    }

    fun setSubmissionEnabled(enabled: Boolean) {
        canSubmit = enabled
        updateSubmitButtonState()
    }

    fun setSubmitting(submitting: Boolean) {
        isSubmitting = submitting
        updateSubmitButtonState()
    }

    fun setHistoryEnabled(enabled: Boolean) {
        isHistoryEnabled = enabled
        historyButton.isEnabled = enabled
        historyButton.alpha = if (enabled) 1f else 0.5f
    }

    fun setFormEnabled(enabled: Boolean) {
        isFormEnabled = enabled
        promptInput.isEnabled = enabled
        modelInputLayout.isEnabled = enabled
        modelInput.isEnabled = enabled
        reasoningTile.isEnabled = enabled
        reasoningSwitch.isEnabled = enabled
        visionTile.isEnabled = enabled
        visionSwitch.isEnabled = enabled
        advancedHeader.isEnabled = enabled
        advancedHeader.alpha = if (enabled) 1f else 0.6f
        maxStepsInput.isEnabled = enabled
        timeoutInput.isEnabled = enabled
        temperatureInput.isEnabled = enabled
        updateToggleTiles()
        updateSubmitButtonState()
        syncSettingTileHeights()
    }

    fun clearPrompt() {
        promptInput.setText("")
        promptInputLayout.error = null
    }

    fun clearTaskId() {
        // No-op. Task IDs are surfaced through task details metadata only.
    }

    fun showTaskCreated(taskId: String) {
        if (taskId.isBlank()) return
        showStatus(context.getString(R.string.task_prompt_started), StatusKind.SUCCESS)
    }

    fun showStatus(message: String?, kind: StatusKind) {
        if (message.isNullOrBlank()) {
            statusText.visibility = View.GONE
            statusText.text = ""
            return
        }

        statusText.visibility = View.VISIBLE
        statusText.text = message
        val colorRes = when (kind) {
            StatusKind.INFO -> R.color.task_prompt_text_secondary
            StatusKind.SUCCESS -> R.color.task_prompt_accent_light
            StatusKind.ERROR -> R.color.task_prompt_error
        }
        statusText.setTextColor(ContextCompat.getColor(context, colorRes))
    }

    fun clearStatus() {
        showStatus(null, StatusKind.INFO)
    }

    private fun updateSubmitButtonState() {
        val showRunningState = taskState?.isBlocking == true && taskState?.cancelInFlight != true
        submitButton.text = when {
            isSubmitting -> context.getString(R.string.task_prompt_starting_button)
            showRunningState -> context.getString(R.string.task_prompt_running_button)
            else -> context.getString(R.string.task_prompt_submit)
        }
        submitProgress.visibility = if (showRunningState) View.VISIBLE else View.GONE

        cancelTaskButton.text = if (taskState?.cancelInFlight == true) {
            context.getString(R.string.task_prompt_cancelling_button)
        } else {
            context.getString(R.string.task_prompt_cancel_button)
        }

        val hasModels = modelOptions.isNotEmpty()
        submitButton.isEnabled =
            isFormEnabled && canSubmit && !isModelsLoading && !isSubmitting && !showRunningState && hasModels
        modelInputLayout.isEnabled = isFormEnabled && !isModelsLoading && hasModels
        cancelTaskButton.isEnabled =
            taskState?.canCancel == true && taskState?.cancelInFlight != true
        historyButton.isEnabled = isHistoryEnabled
        historyButton.alpha = if (isHistoryEnabled) 1f else 0.5f
    }

    private fun buildDraft(): PortalTaskDraft? {
        promptInputLayout.error = null
        modelInputLayout.error = null
        maxStepsInputLayout.error = null
        timeoutInputLayout.error = null
        temperatureInputLayout.error = null

        val prompt = promptInput.text?.toString()?.trim().orEmpty()
        if (prompt.isBlank()) {
            promptInputLayout.error = context.getString(R.string.task_prompt_required)
            return null
        }

        val modelId = selectedModelId
        if (modelId.isNullOrBlank()) {
            modelInputLayout.error = context.getString(R.string.task_prompt_model_required)
            return null
        }

        val maxSteps = maxStepsInput.text?.toString()?.trim()?.toIntOrNull()
        if (maxSteps == null || maxSteps !in 1..10_000) {
            maxStepsInputLayout.error = context.getString(R.string.task_prompt_invalid_max_steps)
            return null
        }

        val executionTimeout = timeoutInput.text?.toString()?.trim()?.toIntOrNull()
        if (executionTimeout == null || executionTimeout !in 1..3600) {
            timeoutInputLayout.error = context.getString(R.string.task_prompt_invalid_timeout)
            return null
        }

        val temperature = temperatureInput.text?.toString()?.trim()?.toDoubleOrNull()
        if (temperature == null || temperature < 0 || temperature > 2) {
            temperatureInputLayout.error = context.getString(R.string.task_prompt_invalid_temperature)
            return null
        }

        currentSettings = PortalTaskSettings(
            llmModel = modelId,
            reasoning = reasoningSwitch.isChecked,
            vision = visionSwitch.isChecked,
            maxSteps = maxSteps,
            temperature = temperature,
            executionTimeout = executionTimeout,
        )

        return PortalTaskDraft(
            prompt = prompt,
            settings = currentSettings,
        )
    }

    private fun selectModel(modelId: String?) {
        if (modelOptions.isEmpty()) return
        val selected = modelOptions.firstOrNull { it.id == modelId }
            ?: modelOptions.firstOrNull { it.id == PortalCloudClient.DEFAULT_MODEL_ID }
            ?: modelOptions.firstOrNull()
        selected?.let {
            selectedModelId = it.id
            modelInput.setText(it.label)
        }
    }

    private fun applyTaskState(state: TaskStateViewModel?) {
        taskStateContainer.visibility = if (state == null) View.GONE else View.VISIBLE
        if (state == null) {
            latestTaskLabel.visibility = View.GONE
            taskStateSummary.visibility = View.GONE
            taskStateDetail.visibility = View.GONE
            cancelTaskButton.visibility = View.GONE
            taskStateContainer.isClickable = false
            taskStateContainer.isFocusable = false
            taskStateContainer.setOnClickListener(null)
            return
        }

        latestTaskLabel.visibility = View.VISIBLE
        taskStateChip.text = state.statusLabel
        taskStateChip.background = createChipBackground(state.statusKind)
        taskStateChip.setTextColor(ContextCompat.getColor(context, R.color.task_prompt_text_primary))

        taskStateDetail.text = state.promptPreview.orEmpty()
        taskStateDetail.visibility = if (state.promptPreview.isNullOrBlank()) View.GONE else View.VISIBLE

        taskStateSummary.text = state.summary.orEmpty()
        taskStateSummary.visibility = if (state.summary.isNullOrBlank()) View.GONE else View.VISIBLE

        cancelTaskButton.visibility =
            if (state.canCancel || state.cancelInFlight) View.VISIBLE else View.GONE

        taskStateContainer.isClickable = state.isClickable
        taskStateContainer.isFocusable = state.isClickable
        taskStateContainer.setOnClickListener(
            if (state.isClickable) {
                View.OnClickListener { onOpenTaskDetails(state.taskId) }
            } else {
                null
            },
        )
    }

    private fun createChipBackground(kind: StatusKind): GradientDrawable {
        val backgroundColor = when (kind) {
            StatusKind.INFO -> ContextCompat.getColor(context, R.color.task_prompt_chip_info_bg)
            StatusKind.SUCCESS -> ContextCompat.getColor(context, R.color.task_prompt_chip_success_bg)
            StatusKind.ERROR -> ContextCompat.getColor(context, R.color.task_prompt_chip_error_bg)
        }

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            setColor(backgroundColor)
        }
    }

    private fun updateToggleTiles() {
        updateToggleTile(reasoningTile, reasoningSwitch.isChecked)
        updateToggleTile(visionTile, visionSwitch.isChecked)
    }

    private fun syncSettingTileHeights() {
        val tileRow = reasoningTile.parent as? View ?: return
        resetTileHeight(reasoningTile)
        resetTileHeight(visionTile)
        tileRow.doOnLayout {
            val maxHeight = maxOf(
                measureTileHeight(reasoningTile),
                measureTileHeight(visionTile),
            )
            if (maxHeight <= 0) return@doOnLayout
            applyTileHeight(reasoningTile, maxHeight)
            applyTileHeight(visionTile, maxHeight)
        }
    }

    private fun resetTileHeight(card: MaterialCardView) {
        val layoutParams = card.layoutParams ?: return
        if (layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT) return
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        card.layoutParams = layoutParams
    }

    private fun measureTileHeight(card: MaterialCardView): Int {
        if (card.width <= 0) {
            return card.measuredHeight
        }
        card.measure(
            View.MeasureSpec.makeMeasureSpec(card.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return card.measuredHeight
    }

    private fun applyTileHeight(card: MaterialCardView, height: Int) {
        val layoutParams = card.layoutParams ?: return
        if (layoutParams.height == height) return
        layoutParams.height = height
        card.layoutParams = layoutParams
    }

    private fun updateToggleTile(card: MaterialCardView, isChecked: Boolean) {
        val backgroundColor = ContextCompat.getColor(
            context,
            if (isChecked) R.color.task_prompt_chip_info_bg else R.color.task_prompt_input_surface,
        )
        val strokeColor = ContextCompat.getColor(
            context,
            if (isChecked) R.color.task_prompt_accent else R.color.task_prompt_stroke,
        )
        card.setCardBackgroundColor(backgroundColor)
        card.strokeColor = strokeColor
        card.alpha = if (isFormEnabled) 1f else 0.6f
    }

    private fun openModelPicker() {
        if (!isFormEnabled || modelOptions.isEmpty()) return

        val dialog = BottomSheetDialog(context)
        val dialogBinding = DialogTaskPromptModelPickerBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(dialogBinding.root)

        val adapter = ModelPickerAdapter { selected ->
            selectedModelId = selected.id
            modelInputLayout.error = null
            modelInput.setText(selected.label)
            dialog.dismiss()
        }

        dialogBinding.taskPromptModelList.layoutManager = LinearLayoutManager(context)
        dialogBinding.taskPromptModelList.adapter = adapter
        dialogBinding.taskPromptModelList.itemAnimator = null

        fun updateFilteredOptions(query: String) {
            val normalizedQuery = query.trim().lowercase()
            filteredModelOptions =
                if (normalizedQuery.isBlank()) {
                    modelOptions
                } else {
                    modelOptions.filter { option ->
                        option.label.lowercase().contains(normalizedQuery) ||
                        option.id.lowercase().contains(normalizedQuery)
                    }
                }
            adapter.notifyDataSetChanged()
            dialogBinding.taskPromptModelEmptyText.visibility =
                if (filteredModelOptions.isEmpty()) View.VISIBLE else View.GONE
        }

        dialogBinding.taskPromptModelSearchInput.doAfterTextChanged { editable ->
            updateFilteredOptions(editable?.toString().orEmpty())
        }
        dialogBinding.taskPromptModelCloseButton.setOnClickListener { dialog.dismiss() }

        updateFilteredOptions("")
        dialog.setOnShowListener {
            dialog.behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
                isDraggable = false
            }
        }
        dialog.show()
    }

    private fun formatTemperature(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }

    private inner class ModelPickerAdapter(
        private val onModelSelected: (PortalModelOption) -> Unit,
    ) : RecyclerView.Adapter<ModelPickerAdapter.ModelViewHolder>() {
        private val inflater = LayoutInflater.from(context)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
            val binding = ItemTaskPromptModelOptionBinding.inflate(inflater, parent, false)
            return ModelViewHolder(binding)
        }

        override fun getItemCount(): Int = filteredModelOptions.size

        override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
            holder.bind(filteredModelOptions[position])
        }

        inner class ModelViewHolder(
            private val itemBinding: ItemTaskPromptModelOptionBinding,
        ) : RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(option: PortalModelOption) {
                itemBinding.taskPromptModelOptionTitle.text = option.label
                itemBinding.taskPromptModelOptionSubtitle.text = option.id

                val isSelected = option.id == selectedModelId
                itemBinding.taskPromptModelOptionSelectedIcon.visibility =
                    if (isSelected) View.VISIBLE else View.INVISIBLE
                itemBinding.taskPromptModelOptionCard.strokeColor = ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.task_prompt_accent else R.color.task_prompt_stroke,
                )
                itemBinding.taskPromptModelOptionCard.setCardBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        if (isSelected) R.color.task_prompt_chip_info_bg else R.color.task_prompt_input_surface,
                    ),
                )
                itemBinding.taskPromptModelOptionCard.setOnClickListener { onModelSelected(option) }
            }
        }
    }
}
