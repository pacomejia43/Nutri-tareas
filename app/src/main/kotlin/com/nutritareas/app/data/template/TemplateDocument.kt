package com.nutritareas.app.data.template

/**
 * The single Google Doc she always works from - fixed on purpose, not user-picked (see the
 * "Plantilla" button in ChatScreen).
 */
object TemplateDocument {
    const val ID: String = "1gy_S-aNGET0DQDwp8hLKemsyMahGtfAFBH3gyEp80eA"

    /**
     * `authuser=0` pins the link to whichever Google account is first on the device instead of
     * letting Google show its "choose an account" interstitial on every open - the doc is shared
     * so any account can edit it, so there's nothing to actually choose.
     */
    const val EDIT_URL: String = "https://docs.google.com/document/d/$ID/edit?tab=t.0&authuser=0"
}
