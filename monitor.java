import org.json.simple.*;
import java.io.*;
import org.json.simple.parser.*;
import twitter4j.conf.*;
import java.util.*;
import twitter4j.*;

public class monitor implements Runnable
{
    private final ArrayList<String> accounts;
    private HashMap<String, String> hookNotifyRole;
    private HashMap<String, String[]> hookEmbeds;
    private HashMap<String, ArrayList<String>> accountHooks;
    private final ArrayList<ArrayList<String>> keys;
    private ArrayList<Status> postedTweets;
    private ArrayList<String> keywords;
    private ArrayList<String> negativeKeywords;
    private final int POSTTWEETSBACKLOG;
    private int twitterRotate;
    private final int KEYS;
    private long listID;
    private int delay;
    private int attempts;
    
    public monitor() throws IOException, ParseException {
        final JSONParser parser = new JSONParser();
        final FileReader fileReader = new FileReader("config.txt");
        final JSONObject config = (JSONObject)parser.parse(fileReader);
        this.delay = Integer.parseInt(config.get("delay"));
        this.listID = config.get("listID");
        this.attempts = 0;
        this.keys = new ArrayList<ArrayList<String>>();
        final FileReader keyFile = new FileReader("keyPool.txt");
        final JSONObject key = (JSONObject)parser.parse(keyFile);
        for (int x = 0; x < key.size(); ++x) {
            final JSONArray keyArray = key.get(new StringBuilder().append(x + 1).toString());
            this.keys.add(keyArray);
        }
        System.out.println(this.keys);
        this.KEYS = this.keys.size();
        this.POSTTWEETSBACKLOG = 7;
        this.keywords = new ArrayList<String>();
        this.negativeKeywords = new ArrayList<String>();
        final Scanner scanKeywords = new Scanner(new File("keywords.txt"));
        while (scanKeywords.hasNextLine()) {
            final String keyword = scanKeywords.nextLine();
            if (keyword.startsWith("-")) {
                this.negativeKeywords.add(keyword.substring(1));
            }
            else {
                this.keywords.add(keyword);
            }
        }
        final FileReader embedFile = new FileReader("embed.txt");
        final JSONObject embed = (JSONObject)parser.parse(embedFile);
        final int webhooksAmt = embed.size();
        final String[][] embeds = new String[webhooksAmt][3];
        for (int x2 = 0; x2 < embed.size(); ++x2) {
            final JSONArray embedArray = embed.get(new StringBuilder().append(x2 + 1).toString());
            embeds[x2][0] = embedArray.get(0);
            embeds[x2][1] = embedArray.get(1);
            embeds[x2][2] = embedArray.get(2);
        }
        this.accounts = new ArrayList<String>();
        this.hookEmbeds = new HashMap<String, String[]>();
        this.accountHooks = new HashMap<String, ArrayList<String>>();
        this.hookNotifyRole = new HashMap<String, String>();
        for (int x2 = 0; x2 < webhooksAmt; ++x2) {
            final Scanner scanAccounts = new Scanner(new File(String.valueOf(x2 + 1) + ".txt"));
            final String hook = scanAccounts.nextLine();
            final String role = scanAccounts.nextLine();
            if (role.contains("<")) {
                this.hookNotifyRole.put(hook, role);
            }
            this.hookEmbeds.put(hook, embeds[x2]);
            while (scanAccounts.hasNextLine()) {
                final String account = scanAccounts.nextLine().toLowerCase();
                if (!this.accounts.contains(account)) {
                    this.accounts.add(account);
                    this.accountHooks.put(account, new ArrayList<String>());
                }
                this.accountHooks.get(account).add(hook);
            }
            scanAccounts.close();
        }
    }
    
    @Override
    public void run() {
        final ArrayList<Twitter> twitters = new ArrayList<Twitter>();
        this.postedTweets = new ArrayList<Status>();
        for (int x = 0; x < this.KEYS; ++x) {
            final ConfigurationBuilder cb = new ConfigurationBuilder();
            cb.setDebugEnabled(false).setOAuthConsumerKey(this.keys.get(x).get(0)).setOAuthConsumerSecret(this.keys.get(x).get(1)).setOAuthAccessToken(this.keys.get(x).get(2)).setOAuthAccessTokenSecret(this.keys.get(x).get(3));
            final TwitterFactory tf = new TwitterFactory(cb.build());
            final Twitter twt = tf.getInstance();
            twitters.add(twt);
        }
        this.twitterRotate = 0;
        List<Status> timeline = null;
        List<Status> oldTimeline = null;
        final Paging paging = new Paging(1, 20);
        try {
            timeline = twitters.get(this.twitterRotate % this.KEYS).getUserListStatuses(this.listID, paging);
        }
        catch (TwitterException e) {
            e.printStackTrace();
        }
        catch (IndexOutOfBoundsException e2) {
            System.out.println("No tweets found");
        }
        oldTimeline = timeline;
        while (true) {
            try {
                Thread.sleep(this.delay);
            }
            catch (InterruptedException ex) {}
            ++this.attempts;
            ++this.twitterRotate;
            try {
                timeline = twitters.get(this.twitterRotate % this.KEYS).getUserListStatuses(this.listID, paging);
            }
            catch (TwitterException e) {
                e.printStackTrace();
            }
            if (!oldTimeline.equals(timeline)) {
                final List<Status> newTweets = new ArrayList<Status>(timeline);
                newTweets.removeAll(oldTimeline);
                for (final Status tweet : newTweets) {
                    this.newTweet(tweet);
                }
                oldTimeline = timeline;
            }
            System.out.println("Attempt #" + this.attempts + " ... (key- " + this.twitterRotate % this.KEYS + ") retrying list:" + this.listID + " [delay:" + this.delay + "ms]");
        }
    }
    
    public void newTweet(final Status tweet) {
        if (!tweet.isRetweet()) {
            final long time = tweet.getCreatedAt().getTime();
            final Date today = Calendar.getInstance().getTime();
            final long epochTime = today.getTime();
            final long diff = epochTime - time;
            if (diff / 1000L > 20L) {
                System.out.println("Difference:" + diff + ", greater than 20 secs");
                return;
            }
            boolean notify = false;
            final String caption = tweet.getText();
            if (this.hasNegativeKeywords(caption)) {
                return;
            }
            Status quotedStatus = tweet.getQuotedStatus();
            try {
                final String quoteCaption = quotedStatus.getText();
                if (this.hasNegativeKeywords(quoteCaption)) {
                    return;
                }
                notify = this.hasPositiveKeywords(quoteCaption);
            }
            catch (NullPointerException e) {
                quotedStatus = null;
            }
            String videoType;
            try {
                videoType = tweet.getMediaEntities()[0].getType();
            }
            catch (ArrayIndexOutOfBoundsException e2) {
                videoType = null;
            }
            if (!this.postedTweets.contains(tweet)) {
                System.out.println("New tweet - attempts reset (Previous attempts: " + this.attempts + ")");
                System.out.println(tweet.getText());
                notify = this.hasPositiveKeywords(caption);
                this.postDiscord(tweet.getUser().getScreenName(), tweet.getUser().getName(), tweet.getURLEntities(), tweet.getMediaEntities(), caption, tweet.getUser().getBiggerProfileImageURL(), tweet.getId(), quotedStatus, videoType, notify);
                this.postedTweets.add(tweet);
                this.attempts = 1;
            }
            else {
                System.out.println("Found an already posted tweet");
            }
            if (this.postedTweets.size() > this.POSTTWEETSBACKLOG) {
                this.postedTweets.remove(0);
            }
        }
    }
    
    public void postDiscord(final String account, final String twitterName, final URLEntity[] links, final MediaEntity[] media, final String caption, final String profilePic, final long id, final Status quotedStatus, final String mediaType, final boolean notify) {
        final ArrayList<String> hooks = this.accountHooks.get(account.toLowerCase());
        if (hooks != null) {
            for (final String hook : hooks) {
                final String[] embeds = this.hookEmbeds.get(hook);
                final Discord d = new Discord(embeds[0], embeds[1], embeds[2]);
                d.webHookMessage(account, twitterName, hook, links, media, caption, profilePic, id, quotedStatus, mediaType, notify, this.hookNotifyRole);
            }
        }
    }
    
    public boolean hasNegativeKeywords(final String caption) {
        for (final String neg : this.negativeKeywords) {
            if (caption.toLowerCase().contains(neg.toLowerCase())) {
                System.out.println("Negative keyword: " + neg);
                return true;
            }
        }
        return false;
    }
    
    public boolean hasPositiveKeywords(final String caption) {
        for (final String key : this.keywords) {
            if (caption.toLowerCase().contains(key.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
