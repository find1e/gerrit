//Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.client.diff;

import com.google.gerrit.client.AvatarImage;
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.CommentApi;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.changes.CommentInput;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import net.codemirror.lib.CodeMirror;

/** An HtmlPanel for displaying a published comment */
class PublishedBox extends CommentBox {
  interface Binder extends UiBinder<HTMLPanel, PublishedBox> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  static interface Style extends CssResource {
    String closed();
  }

  private final SideBySide2 parent;
  private final PatchSet.Id psId;
  private final CommentInfo comment;
  private DraftBox replyBox;

  @UiField Style style;
  @UiField Widget header;
  @UiField Element name;
  @UiField Element summary;
  @UiField Element date;
  @UiField Element message;
  @UiField Element buttons;
  @UiField Button reply;
  @UiField Button done;

  @UiField(provided = true)
  AvatarImage avatar;

  PublishedBox(
      SideBySide2 parent,
      CodeMirror cm,
      DisplaySide side,
      CommentLinkProcessor clp,
      PatchSet.Id psId,
      CommentInfo info) {
    super(cm, info, side);

    this.parent = parent;
    this.psId = psId;
    this.comment = info;

    if (info.author() != null) {
      avatar = new AvatarImage(info.author());
      avatar.setSize("", "");
    } else {
      avatar = new AvatarImage();
    }

    initWidget(uiBinder.createAndBindUi(this));
    header.addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        setOpen(!isOpen());
      }
    }, ClickEvent.getType());

    name.setInnerText(authorName(info));
    date.setInnerText(FormatUtil.shortFormatDayTime(info.updated()));
    if (info.message() != null) {
      String msg = info.message().trim();
      summary.setInnerText(msg);
      message.setInnerSafeHtml(clp.apply(
          new SafeHtmlBuilder().append(msg).wikify()));
    }
  }

  @Override
  CommentInfo getCommentInfo() {
    return comment;
  }

  @Override
  boolean isOpen() {
    return UIObject.isVisible(message);
  }

  void setOpen(boolean open) {
    UIObject.setVisible(summary, !open);
    UIObject.setVisible(message, open);
    UIObject.setVisible(buttons, open);
    if (open) {
      removeStyleName(style.closed());
    } else {
      addStyleName(style.closed());
    }
    super.setOpen(open);
  }

  void registerReplyBox(DraftBox box) {
    replyBox = box;
    box.registerReplyToBox(this);
  }

  void unregisterReplyBox() {
    replyBox = null;
  }

  private void openReplyBox() {
    replyBox.setOpen(true);
    replyBox.setEdit(true);
  }

  DraftBox addReplyBox() {
    DraftBox box = parent.addDraftBox(parent.createReply(comment), getSide());
    registerReplyBox(box);
    return box;
  }

  void doReply() {
    if (!Gerrit.isSignedIn()) {
      Gerrit.doSignIn(parent.getToken());
    } else if (replyBox == null) {
      DraftBox box = addReplyBox();
      if (!getCommentInfo().has_line()) {
        parent.addFileCommentBox(box);
      }
    } else {
      openReplyBox();
    }
  }

  @UiHandler("reply")
  void onReply(ClickEvent e) {
    e.stopPropagation();
    doReply();
  }

  @UiHandler("done")
  void onReplyDone(ClickEvent e) {
    e.stopPropagation();
    if (!Gerrit.isSignedIn()) {
      Gerrit.doSignIn(parent.getToken());
    } else if (replyBox == null) {
      done.setEnabled(false);
      CommentInput input = CommentInput.create(parent.createReply(comment));
      input.setMessage(PatchUtil.C.cannedReplyDone());
      CommentApi.createDraft(psId, input,
          new GerritCallback<CommentInfo>() {
            @Override
            public void onSuccess(CommentInfo result) {
              done.setEnabled(true);
              setOpen(false);
              DraftBox box = parent.addDraftBox(result, getSide());
              registerReplyBox(box);
              if (!getCommentInfo().has_line()) {
                parent.addFileCommentBox(box);
              }
            }
          });
    } else {
      openReplyBox();
      setOpen(false);
    }
  }

  private static String authorName(CommentInfo info) {
    if (info.author() != null) {
      if (info.author().name() != null) {
        return info.author().name();
      }
      return Gerrit.getConfig().getAnonymousCowardName();
    }
    return Util.C.messageNoAuthor();
  }
}