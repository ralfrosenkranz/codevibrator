package de.ralfrosenkranz.codevibrator.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.ralfrosenkranz.codevibrator.ui.LafSupport;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectConfig {
    public String activeProfile = "default";
    public String promptBase = "Please apply the changes from the attached zip to my project. Return only the patch/diff instructions and do not invent files.";
    public List<String> promptAddOns = new ArrayList<>();
    public List<String> promptHistory = new ArrayList<>();

    /** UI Look&Feel selection (matches LafSupport.MENU_ITEMS entries). */
    public String uiLookAndFeel = LafSupport.LAF_FLAT_INTELLIJ;

    /**
     * Best-effort: try to bring an already-open ChatGPT browser window to front
     * instead of opening a new one. If no existing window can be found, CodeVibrator
     * will open the ChatGPT URL normally.
     */
    public boolean reuseExistingChatGptWindow = true;

    /** ChatGPT URL to open. */
    public String chatGptUrl = "https://chat.openai.com/";
}
