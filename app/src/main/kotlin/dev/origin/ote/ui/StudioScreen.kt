package dev.origin.ote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.origin.ote.R
import dev.origin.ote.data.PresetKind
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(viewModel:OteViewModel,kind:dev.origin.ote.model.ThemeKind,initialId:String?=null,onExport:(String,StudioTransform)->Unit,
                 onImportPng:()->Unit,onImportGif:()->Unit) {
    val allThemes by viewModel.themes.collectAsStateWithLifecycle()
    val themes = allThemes.filter { it.kind == kind }
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable(kind, initialId) { mutableStateOf(initialId) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var themeMenu by remember { mutableStateOf(false) }
    var presetMenu by remember { mutableStateOf(false) }
    var edit by rememberSaveable { mutableStateOf(StudioTransform()) }
    var playing by rememberSaveable { mutableStateOf(true) }
    var playhead by rememberSaveable { mutableIntStateOf(0) }
    var showCenter by rememberSaveable { mutableStateOf(true) }
    var panel by rememberSaveable { mutableIntStateOf(0) }
    var easing by rememberSaveable { mutableStateOf(StudioEasing.LINEAR) }
    val selected=themes.firstOrNull { it.id==selectedId }?:themes.firstOrNull()
    val sourceCanvas=selected?.manifest?.canvas
    var widthText by rememberSaveable(selected?.id) { mutableStateOf((edit.outputWidth?:sourceCanvas?.width)?.toString()?:"") }
    var heightText by rememberSaveable(selected?.id) { mutableStateOf((edit.outputHeight?:sourceCanvas?.height)?.toString()?:"") }
    var anchorXText by rememberSaveable(selected?.id) { mutableStateOf((edit.sourceAnchorX?:sourceCanvas?.anchorX?:sourceCanvas?.width?.div(2f))?.toString()?:"") }
    var anchorYText by rememberSaveable(selected?.id) { mutableStateOf((edit.sourceAnchorY?:sourceCanvas?.anchorY?:sourceCanvas?.height?.div(2f))?.toString()?:"") }
    LaunchedEffect(selected?.id) {
        if(editingId!=selected?.id) {
            edit=StudioTransform();playhead=0;editingId=selected?.id
            widthText=sourceCanvas?.width?.toString()?:"";heightText=sourceCanvas?.height?.toString()?:""
            anchorXText=(sourceCanvas?.anchorX?:sourceCanvas?.width?.div(2f))?.toString()?:""
            anchorYText=(sourceCanvas?.anchorY?:sourceCanvas?.height?.div(2f))?.toString()?:""
        }
    }
    val widthValid=widthText.toIntOrNull()?.let { it in 1..1260 }==true
    val heightValid=heightText.toIntOrNull()?.let { it in 1..2800 }==true
    val anchorXValid=anchorXText.toFloatOrNull()?.let { it.isFinite() && sourceCanvas!=null && it in 0f..sourceCanvas.width.toFloat() }==true
    val anchorYValid=anchorYText.toFloatOrNull()?.let { it.isFinite() && sourceCanvas!=null && it in 0f..sourceCanvas.height.toFloat() }==true
    val total=selected?.manifest?.frames?.size?:0
    val indices=if(total>0)runCatching { StudioTimeline.frameIndices(total,edit) }.getOrDefault(emptyList()) else emptyList()
    val timelineMs=if(selected!=null)indices.sumOf { StudioTimeline.durationAt(edit,it,selected.manifest.frames[it].durationMs).toLong() } else 0L
    val valid=selected!=null && widthValid && heightValid && anchorXValid && anchorYValid && timelineMs in 5..120_000 &&
        runCatching { StudioTimeline.validate(selected.manifest.canvas,total,edit) }.isSuccess
    fun selectFrame(index:Int) {
        if(total==0)return
        val at=index.coerceIn(0,total-1)
        playing=false;playhead=at
        edit=StudioTimeline.withPose(edit,StudioTimeline.poseAt(edit,at))
        easing=edit.keyframes.firstOrNull { it.frame==at }?.easing?:StudioEasing.LINEAR
    }
    fun updatePose(change:(StudioPose)->StudioPose) {
        val pose=if(playing)StudioTimeline.poseAt(edit,playhead) else StudioTimeline.basePose(edit)
        edit=StudioTimeline.withPose(edit,change(pose));playing=false
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Text(stringResource(if(kind==dev.origin.ote.model.ThemeKind.AOD) R.string.aod_studio_title else R.string.fp_studio_title),style=MaterialTheme.typography.headlineMedium)
            if(kind==dev.origin.ote.model.ThemeKind.AOD) Text(stringResource(R.string.aod_studio_scope),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.studio_timeline_subtitle),style=MaterialTheme.typography.bodyMedium)
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick=onImportPng,enabled=!busy,modifier=Modifier.weight(1f)) { Text(stringResource(R.string.import_png)) }
                FilledTonalButton(onClick=onImportGif,enabled=!busy,modifier=Modifier.weight(1f)) { Text(stringResource(R.string.import_gif)) }
            }
            if(kind==dev.origin.ote.model.ThemeKind.FINGERPRINT) Box {
                TextButton(onClick={presetMenu=true},enabled=!busy) { Text(stringResource(R.string.studio_presets)) }
                DropdownMenu(expanded=presetMenu,onDismissRequest={presetMenu=false}) {
                    PresetKind.entries.forEach { kind ->
                        DropdownMenuItem(text={Text(stringResource(presetLabel(kind)))},onClick={presetMenu=false;viewModel.createPreset(kind)})
                    }
                }
            }
        }
        if(selected==null) item {
            OteSurface(prefs.style,prefs.reducedMotion,Modifier.fillMaxWidth()) { Text(stringResource(R.string.studio_empty)) }
        } else {
            item {
                ExposedDropdownMenuBox(expanded=themeMenu,onExpandedChange={themeMenu=!themeMenu}) {
                    OutlinedTextField(value=selected.title,onValueChange={},readOnly=true,
                        label={Text(stringResource(R.string.select_theme))},modifier=Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(themeMenu)})
                    ExposedDropdownMenu(expanded=themeMenu,onDismissRequest={themeMenu=false}) {
                        themes.forEach { item -> DropdownMenuItem(text={Text(item.title)},onClick={selectedId=item.id;themeMenu=false;edit=StudioTransform();playhead=0}) }
                    }
                }
            }
            item {
                OteSurface(prefs.style,prefs.reducedMotion,Modifier.fillMaxWidth()) {
                    ThemePreview(selected,playing,Modifier.fillMaxWidth().height(330.dp).clip(MaterialTheme.shapes.medium),
                        if(playing)edit else edit.copy(keyframes=emptyList()),frameSelection=if(playing)null else playhead,
                        onFrameChanged={playhead=it},showCenter=showCenter)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick={if(playing)edit=StudioTimeline.withPose(edit,StudioTimeline.poseAt(edit,playhead));playing=!playing}) { Icon(if(playing)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            stringResource(if(playing)R.string.pause else R.string.play)) }
                        Column {
                            Text(stringResource(R.string.timeline_frame_position,playhead+1,total))
                            Text(stringResource(R.string.studio_duration,timelineMs/1000.0),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.studio_center_overlay));Switch(checked=showCenter,onCheckedChange={showCenter=it})
                    }
                }
            }
            item {
                TabRow(selectedTabIndex=panel) {
                    listOf(R.string.studio_panel_timeline,R.string.studio_panel_canvas,R.string.studio_panel_transform).forEachIndexed { index,label ->
                        Tab(selected=panel==index,onClick={panel=index},text={Text(stringResource(label))})
                    }
                }
            }
            if(panel==0) item {
                OteSurface(prefs.style,prefs.reducedMotion,Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.timeline_title),style=MaterialTheme.typography.titleLarge)
                    if(total>1) Slider(value=playhead.toFloat().coerceIn(0f,(total-1).toFloat()),onValueChange={selectFrame(it.roundToInt())},
                        valueRange=0f..(total-1).toFloat(),steps=(total-2).coerceAtLeast(0))
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        items((0 until total).toList(),key={it}) { index ->
                            FilterChip(selected=index==playhead,onClick={selectFrame(index)},
                                label={Text(stringResource(R.string.timeline_frame_chip,index+1))},
                                leadingIcon=if(edit.keyframes.any { it.frame==index }) { {Icon(Icons.Outlined.Star,stringResource(R.string.keyframe_marker),Modifier.size(14.dp))} } else null,
                                colors=FilterChipDefaults.filterChipColors(labelColor=if(index in edit.excludedFrames)MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface))
                        }
                    }
                    Text(stringResource(R.string.timeline_trim_format,edit.trimStart+1,(edit.trimEnd?:total-1)+1,indices.size))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={edit=edit.copy(trimStart=playhead,trimEnd=maxOf(edit.trimEnd?:total-1,playhead))},modifier=Modifier.weight(1f)) { Text(stringResource(R.string.timeline_set_start)) }
                        OutlinedButton(onClick={edit=edit.copy(trimStart=minOf(edit.trimStart,playhead),trimEnd=playhead)},modifier=Modifier.weight(1f)) { Text(stringResource(R.string.timeline_set_end)) }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={edit=edit.copy(trimStart=0,trimEnd=null,excludedFrames=emptyList())},modifier=Modifier.weight(1f)) { Text(stringResource(R.string.timeline_all_frames)) }
                        TextButton(onClick={edit=edit.copy(excludedFrames=if(playhead in edit.excludedFrames)edit.excludedFrames-playhead else (edit.excludedFrames+playhead).distinct())},modifier=Modifier.weight(1f)) {
                            Text(stringResource(if(playhead in edit.excludedFrames)R.string.timeline_include_frame else R.string.timeline_exclude_frame))
                        }
                    }
                    val currentDuration=StudioTimeline.durationAt(edit,playhead,selected.manifest.frames[playhead.coerceIn(0,total-1)].durationMs)
                    var durationText by rememberSaveable(selected.id,playhead,currentDuration) { mutableStateOf(currentDuration.toString()) }
                    val durationValid=durationText.toIntOrNull()?.let { it in 5..10_000 }==true
                    OutlinedTextField(value=durationText,onValueChange={playing=false;durationText=it.filter(Char::isDigit).take(5)},
                        label={Text(stringResource(R.string.timeline_frame_duration))},isError=!durationValid,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={val delay=durationText.toIntOrNull();if(delay!=null && durationValid)edit=edit.copy(frameDurations=edit.frameDurations.filterNot { it.frame==playhead }+StudioFrameDuration(playhead,delay))},enabled=durationValid,modifier=Modifier.weight(1f)) { Text(stringResource(R.string.timeline_save_duration)) }
                        TextButton(onClick={edit=edit.copy(frameDurations=edit.frameDurations.filterNot { it.frame==playhead })},modifier=Modifier.weight(1f)) { Text(stringResource(R.string.timeline_clear_duration)) }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.timeline_keep_source_timing),Modifier.weight(1f));Switch(checked=edit.preserveSourceTiming,onCheckedChange={edit=edit.copy(preserveSourceTiming=it)})
                    }
                    SliderControl(R.string.fps_format,edit.fps.toFloat(),1f..120f,118,edit.fps.toFloat()) { edit=edit.copy(fps=it.roundToInt()) }
                    TextButton(onClick={edit=edit.copy(frameDurations=emptyList(),preserveSourceTiming=false)}) { Text(stringResource(R.string.timeline_uniform_fps)) }
                    Text(stringResource(R.string.timeline_native_timing_note),style=MaterialTheme.typography.bodySmall)
                }
            }
            if(panel==1) item {
                OteSurface(prefs.style,prefs.reducedMotion,Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.studio_canvas_title),style=MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        NumericField(widthText,{text->widthText=text.filter(Char::isDigit).take(4);widthText.toIntOrNull()?.takeIf { it in 1..1260 }?.let { edit=edit.copy(outputWidth=it) }},R.string.studio_canvas_width,!widthValid,Modifier.weight(1f))
                        NumericField(heightText,{text->heightText=text.filter(Char::isDigit).take(4);heightText.toIntOrNull()?.takeIf { it in 1..2800 }?.let { edit=edit.copy(outputHeight=it) }},R.string.studio_canvas_height,!heightValid,Modifier.weight(1f))
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        NumericField(anchorXText,{text->anchorXText=decimal(text);anchorXText.toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..selected.manifest.canvas.width.toFloat() }?.let { edit=edit.copy(sourceAnchorX=it) }},R.string.studio_artwork_anchor_x,!anchorXValid,Modifier.weight(1f),true)
                        NumericField(anchorYText,{text->anchorYText=decimal(text);anchorYText.toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..selected.manifest.canvas.height.toFloat() }?.let { edit=edit.copy(sourceAnchorY=it) }},R.string.studio_artwork_anchor_y,!anchorYValid,Modifier.weight(1f),true)
                    }
                    Text(stringResource(R.string.studio_canvas_help),style=MaterialTheme.typography.bodySmall)
                }
            }
            if(panel==2) item {
                OteSurface(prefs.style,prefs.reducedMotion,Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.keyframe_transform_title),style=MaterialTheme.typography.titleLarge)
                    val controlsPose=if(playing)StudioTimeline.poseAt(edit,playhead) else StudioTimeline.basePose(edit)
                    SliderControl(R.string.scale_format,controlsPose.scale,.1f..3f,display=controlsPose.scale) { value->updatePose { it.copy(scale=value) } }
                    SliderControl(R.string.rotation_format,controlsPose.rotation,-180f..180f,display=controlsPose.rotation) { value->updatePose { it.copy(rotation=value) } }
                    val output=StudioTimeline.outputCanvas(selected.manifest.canvas,edit)
                    SliderControl(R.string.position_x_format,controlsPose.x,-1f..1f,display=controlsPose.x*output.width) { value->updatePose { it.copy(x=value) } }
                    SliderControl(R.string.position_y_format,controlsPose.y,-1f..1f,display=controlsPose.y*output.height) { value->updatePose { it.copy(y=value) } }
                    SliderControl(R.string.opacity_format,controlsPose.alpha,0f..1f,display=controlsPose.alpha*100) { value->updatePose { it.copy(alpha=value) } }
                    OutlinedButton(onClick={val current=if(playing)StudioTimeline.withPose(edit,StudioTimeline.poseAt(edit,playhead)) else edit;playing=false;edit=StudioTimeline.centerAnchor(selected.manifest.canvas,current)},enabled=anchorXValid && anchorYValid && !busy,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.center_artwork_anchor)) }
                    Text(stringResource(R.string.anchor_explanation),style=MaterialTheme.typography.bodySmall)
                    var easingMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick={easingMenu=true}) { Text(stringResource(R.string.keyframe_easing_format,stringResource(easingLabel(easing)))) }
                        DropdownMenu(expanded=easingMenu,onDismissRequest={easingMenu=false}) {
                            StudioEasing.entries.forEach { curve -> DropdownMenuItem(text={Text(stringResource(easingLabel(curve)))},onClick={easing=curve;easingMenu=false}) }
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick={
                            val pose=if(playing)StudioTimeline.poseAt(edit,playhead) else StudioTimeline.basePose(edit)
                            playing=false
                            edit=StudioTimeline.withPose(edit,pose).copy(keyframes=(edit.keyframes.filterNot { it.frame==playhead }+StudioKeyframe(playhead,pose,easing)).sortedBy { it.frame })
                        },enabled=!busy,modifier=Modifier.weight(1f)) { Text(stringResource(R.string.keyframe_save)) }
                        OutlinedButton(onClick={edit=edit.copy(keyframes=edit.keyframes.filterNot { it.frame==playhead })},enabled=edit.keyframes.any { it.frame==playhead } && !busy,modifier=Modifier.weight(1f)) { Text(stringResource(R.string.keyframe_remove)) }
                    }
                    Text(stringResource(R.string.keyframe_count_format,edit.keyframes.size),style=MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.keyframe_help),style=MaterialTheme.typography.bodySmall)
                }
            }
            item {
                OteSurface(prefs.style,prefs.reducedMotion,Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={
                            edit=StudioTransform();playhead=0
                            widthText=selected.manifest.canvas.width.toString();heightText=selected.manifest.canvas.height.toString()
                            anchorXText=(selected.manifest.canvas.anchorX?:selected.manifest.canvas.width/2f).toString()
                            anchorYText=(selected.manifest.canvas.anchorY?:selected.manifest.canvas.height/2f).toString()
                        },enabled=!busy,modifier=Modifier.weight(1f)) { Text(stringResource(R.string.reset_controls)) }
                        Button(onClick={onExport(selected.id,edit)},enabled=!busy && valid,modifier=Modifier.weight(1f)) { Text(stringResource(R.string.export_pack)) }
                    }
                    if(!valid)Text(stringResource(if(indices.isEmpty())R.string.timeline_no_frames else R.string.studio_export_limits_error),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Text(stringResource(R.string.studio_export_explanation),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.studio_remaining_features),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SliderControl(label:Int,value:Float,range:ClosedFloatingPointRange<Float>,steps:Int=0,display:Float,onChange:(Float)->Unit) {
    Text(stringResource(label,display),style=MaterialTheme.typography.labelLarge)
    Slider(value=value,onValueChange=onChange,valueRange=range,steps=steps)
}
@Composable
private fun NumericField(value:String,onChange:(String)->Unit,label:Int,error:Boolean,modifier:Modifier,decimal:Boolean=false) {
    OutlinedTextField(value=value,onValueChange=onChange,label={Text(stringResource(label))},isError=error,modifier=modifier,
        keyboardOptions=KeyboardOptions(keyboardType=if(decimal)KeyboardType.Decimal else KeyboardType.Number),singleLine=true)
}
private fun decimal(text:String)=text.replace(',','.').filter { it.isDigit() || it=='.' }.take(12)
private fun easingLabel(easing:StudioEasing):Int=when(easing) {
    StudioEasing.LINEAR->R.string.keyframe_linear
    StudioEasing.EASE_IN_OUT->R.string.keyframe_ease_in_out
    StudioEasing.EASE_IN->R.string.keyframe_ease_in
    StudioEasing.EASE_OUT->R.string.keyframe_ease_out
}
private fun presetLabel(kind:PresetKind):Int=when(kind) {
    PresetKind.SNOW_BLOOM->R.string.preset_snow_bloom
    PresetKind.WHITE_FEATHER->R.string.preset_white_feather
    PresetKind.SAKURA_PETALS->R.string.preset_sakura_petals
    PresetKind.ANIME_EYE_REVEAL->R.string.preset_anime_eye
    PresetKind.MANGA_TRANSITION->R.string.preset_manga_transition
    PresetKind.CRYSTAL_BURST->R.string.preset_crystal_burst
    PresetKind.SOFT_PULSE->R.string.preset_soft_pulse
    PresetKind.AURORA->R.string.preset_aurora
    PresetKind.BUTTERFLY->R.string.preset_butterfly
}
