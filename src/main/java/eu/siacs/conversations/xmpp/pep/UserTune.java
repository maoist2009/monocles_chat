package eu.siacs.conversations.xmpp.pep;

import java.util.Objects;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;

public class UserTune {

    // Complete details weren't included since we haven't make way to display them in UI.
    public String title;
    public String artist;

    public static UserTune parse(Element items) {
        if (items == null) {
            return null;
        }

        Element item = items.findChild("item");
        if (item == null) {
            return null;
        }

        Element tuneElement = item.findChild("tune", Namespace.USER_TUNE);
        if (tuneElement == null) {
            return null;
        }

        UserTune tune = new UserTune();

        tune.title = tuneElement.findChildContent("title");
        tune.artist = tuneElement.findChildContent("artist");

        if (tune.title == null || tune.title.equals("")) {
            return null;
        }

        if (tune.artist == null || tune.artist.equals("")) {
            return null;
        }

        return tune;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof UserTune)) {
            return false;
        }

        UserTune other = (UserTune) object;

        if (!Objects.equals(title, other.title)) {
            return false;
        }

        if (!Objects.equals(artist, other.artist)) {
            return false;
        }

        return true;
    }
}