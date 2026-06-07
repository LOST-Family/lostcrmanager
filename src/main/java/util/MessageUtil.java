package util;

import java.awt.Color;
import java.util.regex.Matcher;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class MessageUtil {

	public enum EmbedType {
		INFO, SUCCESS, ERROR, LOADING
	}

	public static String footer = "CR Manager | Made by Pixel";

	public static MessageEmbed buildEmbed(String title, String description, EmbedType type, String additionalfooter,
			Field... fields) {
		EmbedBuilder embedreply = new EmbedBuilder();
		embedreply.setTitle(title);
		embedreply.setDescription(description);
		for (Field field : fields) {
			embedreply.addField(field);
		}
		if (footer.equals("")) {
			embedreply.setFooter(footer);
		} else {
			embedreply.setFooter(additionalfooter + "\n" + footer);
		}
		switch (type) {
			case INFO -> embedreply.setColor(Color.CYAN);
			case SUCCESS -> embedreply.setColor(Color.GREEN);
			case ERROR -> embedreply.setColor(Color.RED);
			case LOADING -> embedreply.setColor(Color.MAGENTA);
		}
		return embedreply.build();
	}

	public static MessageEmbed buildEmbed(String title, String description, EmbedType type, Field... fields) {
		return buildEmbed(title, description, type, "", fields);
	}

	public static String unformat(String s) {
		String a = s.replaceAll("_", Matcher.quoteReplacement("\\_")).replaceAll("\\*", Matcher.quoteReplacement("\\*"))
				.replaceAll("~", Matcher.quoteReplacement("\\~")).replaceAll("`", Matcher.quoteReplacement("\\`"))
				.replaceAll("\\|", Matcher.quoteReplacement("\\|")).replaceAll(">", Matcher.quoteReplacement("\\>"))
				.replaceAll("-", Matcher.quoteReplacement("\\-")).replaceAll("#", Matcher.quoteReplacement("\\#"));
		return a;
	}

	public static void sendUserPingHidden(MessageChannelUnion channel, String uuid) {
		channel.sendMessage(".").queue(sentMessage -> {
			new Thread(() -> {
				try {
					Thread.sleep(100);
					sentMessage.editMessage("<@" + uuid + ">").queue();
					Thread.sleep(100);
					sentMessage.delete().queue();
				} catch (InterruptedException e) {
					System.err.println(e.getMessage());
				}
			}).start();
		}, a -> {
		});
	}

	public static void sendUserPingWithDelete(MessageChannelUnion channel, String uuid) {
		Button trashButton = Button.secondary("playerinfo_trash", "\u200B")
				.withEmoji(Emoji.fromUnicode("🗑️"));

		channel.sendMessage(".").queue(sentMessage -> {
			new Thread(() -> {
				try {
					Thread.sleep(100);
					sentMessage.editMessage("<@" + uuid + ">").setActionRow(trashButton).queue();
				} catch (InterruptedException e) {
					System.err.println(e.getMessage());
				}
			}).start();
		});
	}

}
