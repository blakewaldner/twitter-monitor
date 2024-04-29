import twitter4j.*;
import com.mrpowergamerbr.temmiewebhook.embed.*;
import com.mrpowergamerbr.temmiewebhook.*;
import java.util.regex.*;
import java.util.*;

public class Discord
{
    private String color;
    private String footer;
    private String footerIcon;
    private final String quoteColor = "4295f4";
    
    public Discord(final String color, final String footer, final String footerIcon) {
        this.color = color;
        this.footer = footer;
        this.footerIcon = footerIcon;
    }
    
    public void webHookMessage(final String username, final String twitterName, final String hook, final URLEntity[] links, final MediaEntity[] media, String caption, final String profilePic, final long id, final Status quotedStatus, final String mediaType, final boolean notify, final HashMap<String, String> hookNotifyRole) {
        String content = "";
        if (notify) {
            final String role = hookNotifyRole.get(hook);
            if (role != null) {
                content = " Twitter Keywords Match " + role;
            }
        }
        String video = "";
        boolean isVideo = false;
        if (links.length != 0) {
            caption = this.replaceLinks(caption, links);
        }
        if (mediaType != null) {
            if (mediaType.equals("animated_gif") || mediaType.equals("video")) {
                isVideo = true;
                video = media[0].getVideoVariants()[0].getUrl();
            }
            caption = this.cleanEnd(caption);
        }
        boolean isQuote = false;
        if (quotedStatus != null) {
            isQuote = true;
        }
        final FieldEmbed captionEmbed = new FieldEmbed();
        captionEmbed.setName("Content");
        if (caption.equals("")) {
            caption = "No content";
        }
        captionEmbed.setValue(this.clickableTags(caption));
        final TemmieWebhook temmie = new TemmieWebhook(hook);
        final int mediaCount = media.length;
        final DiscordEmbed de = DiscordEmbed.builder().author(AuthorEmbed.builder().name(twitterName).icon_url(profilePic).url("https://twitter.com/" + username).build()).title(String.valueOf(username) + " - Tweet Link").url("https://twitter.com/" + username + "/status/" + id).footer(FooterEmbed.builder().text(this.footer).icon_url(this.footerIcon).build()).build();
        final ArrayList<FieldEmbed> fields = new ArrayList<FieldEmbed>();
        fields.add(captionEmbed);
        if (links.length != 0) {
            final FieldEmbed linksEmbed = new FieldEmbed();
            linksEmbed.setName("Links in Tweet");
            String linkMessage = "";
            for (final URLEntity link : links) {
                linkMessage = String.valueOf(linkMessage) + link.getExpandedURL() + "\n";
            }
            linksEmbed.setValue(linkMessage);
            fields.add(linksEmbed);
        }
        de.setFields(fields);
        if (mediaCount > 0) {
            final ImageEmbed imageEmbed = new ImageEmbed();
            imageEmbed.setUrl(media[0].getMediaURL());
            de.setImage(imageEmbed);
        }
        de.setColor(Integer.parseInt(this.color, 16));
        final DiscordMessage dm = DiscordMessage.builder().embeds(Arrays.asList(de)).content(content).build();
        temmie.sendMessage(dm);
        if (isVideo) {
            final DiscordMessage dm2 = DiscordMessage.builder().content(video).build();
            temmie.sendMessage(dm2);
        }
        if (isQuote) {
            final int mediaCountQ = quotedStatus.getMediaEntities().length;
            String captionQ = quotedStatus.getText();
            if (mediaCountQ > 0) {
                captionQ = this.cleanEnd(captionQ);
            }
            final FieldEmbed captionEmbedQ = new FieldEmbed();
            captionEmbedQ.setName("Content");
            if (captionQ.equals("")) {
                captionQ = "No content";
            }
            captionEmbedQ.setValue(this.clickableTags(captionQ));
            final URLEntity[] linksQ = quotedStatus.getURLEntities();
            final DiscordEmbed quotedMessage = DiscordEmbed.builder().author(AuthorEmbed.builder().name(String.valueOf(quotedStatus.getUser().getName()) + " (Quoted User)").icon_url(quotedStatus.getUser().getBiggerProfileImageURL()).url("https://twitter.com/" + quotedStatus.getUser().getScreenName()).build()).title(String.valueOf(quotedStatus.getUser().getScreenName()) + " - Tweet Link").url("https://twitter.com/" + quotedStatus.getUser().getScreenName() + "/status/" + quotedStatus.getId()).footer(FooterEmbed.builder().text(this.footer).icon_url(this.footerIcon).build()).build();
            final ArrayList<FieldEmbed> fieldsQ = new ArrayList<FieldEmbed>();
            fieldsQ.add(captionEmbedQ);
            if (linksQ.length != 0) {
                final FieldEmbed linksEmbedQ = new FieldEmbed();
                linksEmbedQ.setName("Links in Tweet");
                String linkMessageQ = "";
                URLEntity[] array;
                for (int length2 = (array = linksQ).length, j = 0; j < length2; ++j) {
                    final URLEntity linkk = array[j];
                    linkMessageQ = String.valueOf(linkMessageQ) + linkk.getExpandedURL() + "\n";
                }
                linksEmbedQ.setValue(linkMessageQ);
                fieldsQ.add(linksEmbedQ);
            }
            quotedMessage.setFields(fieldsQ);
            if (mediaCountQ > 0) {
                final ImageEmbed imgQ = new ImageEmbed();
                imgQ.setUrl(quotedStatus.getMediaEntities()[0].getMediaURL());
                quotedMessage.setImage(imgQ);
            }
            quotedMessage.setColor(Integer.parseInt("4295f4", 16));
            final DiscordMessage dmOtherMedia = DiscordMessage.builder().embeds(Arrays.asList(quotedMessage)).build();
            temmie.sendMessage(dmOtherMedia);
        }
        if (mediaCount > 1) {
            for (int x = 1; x < mediaCount; ++x) {
                try {
                    Thread.sleep(500L);
                }
                catch (InterruptedException ex) {}
                final ImageEmbed img = new ImageEmbed();
                img.setUrl(media[x].getMediaURL());
                final DiscordEmbed otherMedia = DiscordEmbed.builder().title(username).description("Picture #" + (x + 1)).image(img).url("https://twitter.com/" + username + "/status/" + id).footer(FooterEmbed.builder().text(this.footer).icon_url(this.footerIcon).build()).build();
                otherMedia.setColor(Integer.parseInt(this.color, 16));
                final DiscordMessage dmOtherMedia2 = DiscordMessage.builder().embeds(Arrays.asList(otherMedia)).build();
                temmie.sendMessage(dmOtherMedia2);
            }
        }
    }
    
    public String clickableTags(String desc) {
        List<String> allMatches = new ArrayList<String>();
        Matcher m = Pattern.compile("(?<=@)[\\w-]+").matcher(desc);
        while (m.find()) {
            allMatches.add(m.group());
        }
        for (final String match : allMatches) {
            desc = desc.replace("@" + match, "[@" + match + "](https://twitter.com/" + match + ")");
        }
        allMatches = new ArrayList<String>();
        m = Pattern.compile("#(\\w*[0-9a-zA-Z]+\\w*[0-9a-zA-Z])").matcher(desc);
        while (m.find()) {
            allMatches.add(m.group());
        }
        for (final String match : allMatches) {
            desc = desc.replace(match, "[" + match + "](https://twitter.com/hashtag/" + match.replace("#", "") + ")");
        }
        return desc;
    }
    
    public String cleanEnd(final String desc) {
        int start;
        for (start = desc.length() - 1; !desc.substring(start).startsWith("https://t.co"); --start) {}
        return desc.substring(0, start);
    }
    
    public String replaceLinks(String message, final URLEntity[] links) {
        for (final URLEntity link : links) {
            message = message.replace(link.getURL(), link.getExpandedURL());
        }
        return message;
    }
}