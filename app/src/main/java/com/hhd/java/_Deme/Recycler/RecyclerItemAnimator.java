/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hhd.java._Deme.Recycler;

import android.support.annotation.NonNull;
import android.support.v4.animation.AnimatorCompatHelper;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorCompat;
import android.support.v4.view.ViewPropertyAnimatorListener;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.SimpleItemAnimator;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView动画系统
 * 提供基本的动画：删除，添加，移动（在recyclerview的Item）
 * <p>
 * This implementation of {@link //RecyclerView.ItemAnimator} provides basic
 * animations on remove, add, and move events that happen to the items in
 * a RecyclerView. RecyclerView uses a DefaultItemAnimator by default.
 *
 * @see //RecyclerView#setItemAnimator(RecyclerView.ItemAnimator)
 */
public class RecyclerItemAnimator extends SimpleItemAnimator {
    private static final boolean DEBUG = false;

    /**
     * 待删除数组列表
     */
    private ArrayList<ViewHolder> mPendingRemovals = new ArrayList<>();
    /**
     * 待添加数组列表
     */
    private ArrayList<ViewHolder> mPendingAdditions = new ArrayList<>();
    /**
     * 待移动数组列表
     */
    private ArrayList<MoveInfo> mPendingMoves = new ArrayList<>();
    /**
     * 待改变数组列表
     */
    private ArrayList<ChangeInfo> mPendingChanges = new ArrayList<>();

    /**
     * 添加数组列表
     */
    ArrayList<ArrayList<ViewHolder>> mAdditionsList = new ArrayList<>();
    /**
     * 移动数组列表
     */
    ArrayList<ArrayList<MoveInfo>> mMovesList = new ArrayList<>();
    /**
     * 改变数组列表
     */
    ArrayList<ArrayList<ChangeInfo>> mChangesList = new ArrayList<>();

    /**
     * 添加动画数组列表
     */
    ArrayList<ViewHolder> mAddAnimations = new ArrayList<>();
    /**
     * 移动动画数组列表
     */
    ArrayList<ViewHolder> mMoveAnimations = new ArrayList<>();
    /**
     * 删除动画数组列表
     */
    ArrayList<ViewHolder> mRemoveAnimations = new ArrayList<>();
    /**
     * 改变动画数组列表
     */
    ArrayList<ViewHolder> mChangeAnimations = new ArrayList<>();

    /**
     * 捕捉移动的信息
     */
    private static class MoveInfo {
        public ViewHolder holder;
        public int fromX, fromY, toX, toY;

        /**
         * @param holder 移动的holder
         * @param fromX  开始的X坐标
         * @param fromY  开始的Y坐标
         * @param toX    停止的X坐标
         * @param toY    停止的Y坐标
         */
        MoveInfo(ViewHolder holder, int fromX, int fromY, int toX, int toY) {
            this.holder = holder;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
        }
    }

    /**
     * 捕捉改变的信息
     */
    private static class ChangeInfo {
        public ViewHolder oldHolder, newHolder;
        public int fromX, fromY, toX, toY;

        /**
         * @param oldHolder 移动前
         * @param newHolder 移动后
         */
        private ChangeInfo(ViewHolder oldHolder, ViewHolder newHolder) {
            this.oldHolder = oldHolder;
            this.newHolder = newHolder;
        }

        /**
         * @param oldHolder 移动前
         * @param newHolder 移动后
         * @param fromX     开始的X坐标
         * @param fromY     开始的Y坐标
         * @param toX       停止的X坐标
         * @param toY       停止的Y坐标
         */
        ChangeInfo(ViewHolder oldHolder, ViewHolder newHolder,
                   int fromX, int fromY, int toX, int toY) {
            this(oldHolder, newHolder);
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
        }

        @Override
        public String toString() {
            return "ChangeInfo{" +
                    "oldHolder=" + oldHolder +
                    ", newHolder=" + newHolder +
                    ", fromX=" + fromX +
                    ", fromY=" + fromY +
                    ", toX=" + toX +
                    ", toY=" + toY +
                    '}';
        }
    }

    /**
     *
     */
    @Override
    public void runPendingAnimations() {
        /** 是否有待删除：true=是有未删除 */
        boolean removalsPending = !mPendingRemovals.isEmpty();
        /** 是否有待移动：true=是有未移动 */
        boolean movesPending = !mPendingMoves.isEmpty();
        /** 是否有待改变：true=是有未改变 */
        boolean changesPending = !mPendingChanges.isEmpty();
        /** 是否有待添加：true=是有未添加 */
        boolean additionsPending = !mPendingAdditions.isEmpty();
        if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
            // nothing to animate
            return;
        }
        // First, remove stuff
        for (ViewHolder holder : mPendingRemovals) {//遍历mPendingRemovals列表
            animateRemoveImpl(holder);/** 删除 */
        }
        mPendingRemovals.clear();
        // Next, move stuff
        if (movesPending) {/** 移动 */
            final ArrayList<MoveInfo> moves = new ArrayList<>();//缓存待移动
            moves.addAll(mPendingMoves);
            mMovesList.add(moves);
            mPendingMoves.clear();
            Runnable mover = new Runnable() {
                @Override
                public void run() {
                    for (MoveInfo moveInfo : moves) {
                        animateMoveImpl(moveInfo.holder, moveInfo.fromX, moveInfo.fromY, moveInfo.toX, moveInfo.toY);
                    }
                    moves.clear();
                    mMovesList.remove(moves);//移动后删除
                }
            };
            if (removalsPending) {//删除之前
                View view = moves.get(0).holder.itemView;
                ViewCompat.postOnAnimationDelayed(view, mover, getRemoveDuration());
            } else {
                mover.run();
            }
        }
        // Next, change stuff, to run in parallel with move animations
        if (changesPending) {/** 改变 */
            final ArrayList<ChangeInfo> changes = new ArrayList<>();//缓存待改变
            changes.addAll(mPendingChanges);
            mChangesList.add(changes);
            mPendingChanges.clear();
            Runnable changer = new Runnable() {
                @Override
                public void run() {
                    for (ChangeInfo change : changes) {
                        animateChangeImpl(change);
                    }
                    changes.clear();
                    mChangesList.remove(changes);
                }
            };
            if (removalsPending) {//删除之前
                ViewHolder holder = changes.get(0).oldHolder;
                ViewCompat.postOnAnimationDelayed(holder.itemView, changer, getRemoveDuration());
            } else {
                changer.run();
            }
        }
        // Next, add stuff
        if (additionsPending) {/** 添加 */
            final ArrayList<ViewHolder> additions = new ArrayList<>();
            additions.addAll(mPendingAdditions);
            mAdditionsList.add(additions);
            mPendingAdditions.clear();
            Runnable adder = new Runnable() {
                @Override
                public void run() {
                    for (ViewHolder holder : additions) {
                        animateAddImpl(holder);
                    }
                    additions.clear();
                    mAdditionsList.remove(additions);
                }
            };
            if (removalsPending || movesPending || changesPending) {//删除之前||移动||改变
                long removeDuration = removalsPending ? getRemoveDuration() : 0;
                long moveDuration = movesPending ? getMoveDuration() : 0;
                long changeDuration = changesPending ? getChangeDuration() : 0;
                long totalDelay = removeDuration + Math.max(moveDuration, changeDuration);
                View view = additions.get(0).itemView;
                ViewCompat.postOnAnimationDelayed(view, adder, totalDelay);
            } else {
                adder.run();
            }
        }
    }

    /**
     * 删除动画
     *
     * @param holder
     * @return
     */
    @Override
    public boolean animateRemove(final ViewHolder holder) {
        resetAnimation(holder);
        mPendingRemovals.add(holder);
        return true;
    }

    /**
     * 删除动画（执行）实现：左右移动（默认上下带渐变）
     */
    private void animateRemoveImpl(final ViewHolder holder) {
        final View view = holder.itemView;
        final ViewPropertyAnimatorCompat animation = ViewCompat.animate(view);
        mRemoveAnimations.add(holder);
        animation.setDuration(getRemoveDuration()).translationX(view.getWidth()).setListener(new VpaListenerAdapter() {//translationX(view.getWidth())：X轴位移整个屏幕
            @Override
            public void onAnimationStart(View view) {
                dispatchRemoveStarting(holder);
            }

            @Override
            public void onAnimationEnd(View view) {
                animation.setListener(null);
                ViewCompat.setTranslationX(view, 0);//X轴位移整个屏幕
                dispatchRemoveFinished(holder);
                mRemoveAnimations.remove(holder);
                dispatchFinishedWhenDone();
            }
        }).start();
    }

    private void animateRemoveImpl_原版(final ViewHolder holder) {
        final View view = holder.itemView;
        final ViewPropertyAnimatorCompat animation = ViewCompat.animate(view);
        mRemoveAnimations.add(holder);
        animation.setDuration(getRemoveDuration()).alpha(0).setListener(new VpaListenerAdapter() {//.alpha(0)：改变透明度
            @Override
            public void onAnimationStart(View view) {
                dispatchRemoveStarting(holder);
            }

            @Override
            public void onAnimationEnd(View view) {
                animation.setListener(null);
                ViewCompat.setAlpha(view, 1);//.setAlpha(view, 1)：改变透明度
                dispatchRemoveFinished(holder);
                mRemoveAnimations.remove(holder);
                dispatchFinishedWhenDone();
            }
        }).start();
    }

    /**
     * 添加动画
     *
     * @param holder
     * @return
     */
    @Override
    public boolean animateAdd(final ViewHolder holder) {
        resetAnimation(holder);
        ViewCompat.setTranslationX(holder.itemView, -holder.itemView.getWidth());//屏幕左侧移出
        mPendingAdditions.add(holder);
        return true;
    }

    public boolean animateAdd_原版(final ViewHolder holder) {
        resetAnimation(holder);
        ViewCompat.setAlpha(holder.itemView, 0);
        mPendingAdditions.add(holder);
        return true;
    }

    /**
     * 添加动画（执行）实现：
     *
     * @param holder
     */
    void animateAddImpl(final ViewHolder holder) {
        final View view = holder.itemView;
        final ViewPropertyAnimatorCompat animation = ViewCompat.animate(view);
        mAddAnimations.add(holder);
        animation.translationX(0).setDuration(getAddDuration()).//添加开始前位置
                setListener(new VpaListenerAdapter() {
            @Override
            public void onAnimationStart(View view) {
                dispatchAddStarting(holder);
            }

            @Override
            public void onAnimationCancel(View view) {
                ViewCompat.setTranslationX(view, 0);//恢复
            }

            @Override
            public void onAnimationEnd(View view) {
                animation.setListener(null);
                dispatchAddFinished(holder);
                mAddAnimations.remove(holder);
                dispatchFinishedWhenDone();
            }
        }).start();
    }

    void animateAddImpl_原版(final ViewHolder holder) {
        final View view = holder.itemView;
        final ViewPropertyAnimatorCompat animation = ViewCompat.animate(view);
        mAddAnimations.add(holder);
        animation.alpha(1).setDuration(getAddDuration()).setListener(new VpaListenerAdapter() {//.alpha(0)：改变透明度
            @Override
            public void onAnimationStart(View view) {
                dispatchAddStarting(holder);
            }

            @Override
            public void onAnimationCancel(View view) {
                ViewCompat.setAlpha(view, 1);
            }

            @Override
            public void onAnimationEnd(View view) {
                animation.setListener(null);
                dispatchAddFinished(holder);
                mAddAnimations.remove(holder);
                dispatchFinishedWhenDone();
            }
        }).start();
    }

    /**
     * 移动动画（删除时使用）
     *
     * @param holder
     * @param fromX
     * @param fromY
     * @param toX
     * @param toY
     * @return
     */
    @Override
    public boolean animateMove(final ViewHolder holder, int fromX, int fromY, int toX, int toY) {
        final View view = holder.itemView;
        fromX += ViewCompat.getTranslationX(holder.itemView);
        fromY += ViewCompat.getTranslationY(holder.itemView);
        resetAnimation(holder);
        int deltaX = toX - fromX;
        int deltaY = toY - fromY;
        if (deltaX == 0 && deltaY == 0) {
            dispatchMoveFinished(holder);
            return false;
        }
        if (deltaX != 0) {
            ViewCompat.setTranslationX(view, -deltaX);
        }
        if (deltaY != 0) {
            ViewCompat.setTranslationY(view, -deltaY);
        }
        mPendingMoves.add(new MoveInfo(holder, fromX, fromY, toX, toY));
        return true;
    }

    public boolean animateMove_原版(final ViewHolder holder, int fromX, int fromY, int toX, int toY) {
        final View view = holder.itemView;
        fromX += ViewCompat.getTranslationX(holder.itemView);
        fromY += ViewCompat.getTranslationY(holder.itemView);
        resetAnimation(holder);
        int deltaX = toX - fromX;
        int deltaY = toY - fromY;
        if (deltaX == 0 && deltaY == 0) {
            dispatchMoveFinished(holder);
            return false;
        }
        if (deltaX != 0) {
            ViewCompat.setTranslationX(view, -deltaX);
        }
        if (deltaY != 0) {
            ViewCompat.setTranslationY(view, -deltaY);
        }
        mPendingMoves.add(new MoveInfo(holder, fromX, fromY, toX, toY));
        return true;
    }

    /**
     * 移动动画实现
     *
     * @param holder
     * @param fromX
     * @param fromY
     * @param toX
     * @param toY
     */
    void animateMoveImpl(final ViewHolder holder, int fromX, int fromY, int toX, int toY) {
        final View view = holder.itemView;
        final int deltaX = toX - fromX;
        final int deltaY = toY - fromY;
        if (deltaX != 0) {
            ViewCompat.animate(view).translationX(0);
        }
        if (deltaY != 0) {
            ViewCompat.animate(view).translationY(0);
        }
        // TODO: make EndActions end listeners instead, since end actions aren't called when
        // vpas are canceled (and can't end them. why?)
        // need listener functionality in VPACompat for this. Ick.
        final ViewPropertyAnimatorCompat animation = ViewCompat.animate(view);
        animation.rotationXBy(360);//围绕X轴旋转360度
        mMoveAnimations.add(holder);
        animation.setDuration(getMoveDuration()).setListener(new VpaListenerAdapter() {
            @Override
            public void onAnimationStart(View view) {
                dispatchMoveStarting(holder);
            }

            @Override
            public void onAnimationCancel(View view) {
                if (deltaX != 0) {
                    ViewCompat.setTranslationX(view, 0);
                }
                if (deltaY != 0) {
                    ViewCompat.setTranslationY(view, 0);
                }
            }

            @Override
            public void onAnimationEnd(View view) {
                animation.setListener(null);
                dispatchMoveFinished(holder);
                mMoveAnimations.remove(holder);
                dispatchFinishedWhenDone();
            }
        }).start();
    }

    void animateMoveImpl_原版(final ViewHolder holder, int fromX, int fromY, int toX, int toY) {
        final View view = holder.itemView;
        final int deltaX = toX - fromX;
        final int deltaY = toY - fromY;
        if (deltaX != 0) {
            ViewCompat.animate(view).translationX(0);
        }
        if (deltaY != 0) {
            ViewCompat.animate(view).translationY(0);
        }
        // TODO: make EndActions end listeners instead, since end actions aren't called when
        // vpas are canceled (and can't end them. why?)
        // need listener functionality in VPACompat for this. Ick.
        final ViewPropertyAnimatorCompat animation = ViewCompat.animate(view);
        mMoveAnimations.add(holder);
        animation.setDuration(getMoveDuration()).setListener(new VpaListenerAdapter() {
            @Override
            public void onAnimationStart(View view) {
                dispatchMoveStarting(holder);
            }

            @Override
            public void onAnimationCancel(View view) {
                if (deltaX != 0) {
                    ViewCompat.setTranslationX(view, 0);
                }
                if (deltaY != 0) {
                    ViewCompat.setTranslationY(view, 0);
                }
            }

            @Override
            public void onAnimationEnd(View view) {
                animation.setListener(null);
                dispatchMoveFinished(holder);
                mMoveAnimations.remove(holder);
                dispatchFinishedWhenDone();
            }
        }).start();
    }

    /**
     * 改变动画
     *
     * @param oldHolder 改变前的Holder
     * @param newHolder 改变后的Holder
     * @param fromX
     * @param fromY
     * @param toX
     * @param toY
     * @return
     */
    @Override
    public boolean animateChange(ViewHolder oldHolder, ViewHolder newHolder,
                                 int fromX, int fromY, int toX, int toY) {
        if (oldHolder == newHolder) {
            // Don't know how to run change animations when the same view holder is re-used.
            // run a move animation to handle position changes.
            return animateMove(oldHolder, fromX, fromY, toX, toY);
        }
        final float prevTranslationX = ViewCompat.getTranslationX(oldHolder.itemView);
        final float prevTranslationY = ViewCompat.getTranslationY(oldHolder.itemView);
        final float prevAlpha = ViewCompat.getAlpha(oldHolder.itemView);
        resetAnimation(oldHolder);
        int deltaX = (int) (toX - fromX - prevTranslationX);
        int deltaY = (int) (toY - fromY - prevTranslationY);
        // recover prev translation state after ending animation
        ViewCompat.setTranslationX(oldHolder.itemView, prevTranslationX);
        ViewCompat.setTranslationY(oldHolder.itemView, prevTranslationY);
        ViewCompat.setAlpha(oldHolder.itemView, prevAlpha);
        if (newHolder != null) {
            // carry over translation values
            resetAnimation(newHolder);
            ViewCompat.setTranslationX(newHolder.itemView, -deltaX);
            ViewCompat.setTranslationY(newHolder.itemView, -deltaY);
            ViewCompat.setTranslationX(newHolder.itemView, -newHolder.itemView.getWidth());//移出
        }
        mPendingChanges.add(new ChangeInfo(oldHolder, newHolder, fromX, fromY, toX, toY));
        return true;
    }

    public boolean animateChange_原版(ViewHolder oldHolder, ViewHolder newHolder,
                                    int fromX, int fromY, int toX, int toY) {
        if (oldHolder == newHolder) {
            // Don't know how to run change animations when the same view holder is re-used.
            // run a move animation to handle position changes.
            return animateMove(oldHolder, fromX, fromY, toX, toY);
        }
        final float prevTranslationX = ViewCompat.getTranslationX(oldHolder.itemView);
        final float prevTranslationY = ViewCompat.getTranslationY(oldHolder.itemView);
        final float prevAlpha = ViewCompat.getAlpha(oldHolder.itemView);
        resetAnimation(oldHolder);
        int deltaX = (int) (toX - fromX - prevTranslationX);
        int deltaY = (int) (toY - fromY - prevTranslationY);
        // recover prev translation state after ending animation
        ViewCompat.setTranslationX(oldHolder.itemView, prevTranslationX);
        ViewCompat.setTranslationY(oldHolder.itemView, prevTranslationY);
        ViewCompat.setAlpha(oldHolder.itemView, prevAlpha);
        if (newHolder != null) {
            // carry over translation values
            resetAnimation(newHolder);
            ViewCompat.setTranslationX(newHolder.itemView, -deltaX);
            ViewCompat.setTranslationY(newHolder.itemView, -deltaY);
            ViewCompat.setAlpha(newHolder.itemView, 0);
        }
        mPendingChanges.add(new ChangeInfo(oldHolder, newHolder, fromX, fromY, toX, toY));
        return true;
    }

    /**
     * 改变动画实现
     *
     * @param changeInfo
     */
    void animateChangeImpl(final ChangeInfo changeInfo) {
        final ViewHolder holder = changeInfo.oldHolder;
        final View view = holder == null ? null : holder.itemView;
        final ViewHolder newHolder = changeInfo.newHolder;
        final View newView = newHolder != null ? newHolder.itemView : null;
        if (view != null) {
            final ViewPropertyAnimatorCompat oldViewAnim = ViewCompat.animate(view).setDuration(
                    getChangeDuration());
            mChangeAnimations.add(changeInfo.oldHolder);
            oldViewAnim.translationX(changeInfo.toX - changeInfo.fromX);
            oldViewAnim.translationY(changeInfo.toY - changeInfo.fromY);
            oldViewAnim.translationX(view.getWidth()).setListener(new VpaListenerAdapter() {//老的移出
                @Override
                public void onAnimationStart(View view) {
                    dispatchChangeStarting(changeInfo.oldHolder, true);
                }

                @Override
                public void onAnimationEnd(View view) {
                    oldViewAnim.setListener(null);
                    ViewCompat.setAlpha(view, 1);
                    ViewCompat.setTranslationX(view, 0);
                    ViewCompat.setTranslationY(view, 0);
                    dispatchChangeFinished(changeInfo.oldHolder, true);
                    mChangeAnimations.remove(changeInfo.oldHolder);
                    dispatchFinishedWhenDone();
                }
            }).start();
        }
        if (newView != null) {
            final ViewPropertyAnimatorCompat newViewAnimation = ViewCompat.animate(newView);
            mChangeAnimations.add(changeInfo.newHolder);
            newViewAnimation.translationX(0).translationY(0).setDuration(getChangeDuration()).
                    alpha(1).setListener(new VpaListenerAdapter() {
                @Override
                public void onAnimationStart(View view) {
                    dispatchChangeStarting(changeInfo.newHolder, false);
                }

                @Override
                public void onAnimationEnd(View view) {
                    newViewAnimation.setListener(null);
                    ViewCompat.setAlpha(newView, 1);
                    ViewCompat.setTranslationX(newView, 0);
                    ViewCompat.setTranslationY(newView, 0);
                    dispatchChangeFinished(changeInfo.newHolder, false);
                    mChangeAnimations.remove(changeInfo.newHolder);
                    dispatchFinishedWhenDone();
                }
            }).start();
        }
    }
    void animateChangeImpl_原版(final ChangeInfo changeInfo) {
        final ViewHolder holder = changeInfo.oldHolder;
        final View view = holder == null ? null : holder.itemView;
        final ViewHolder newHolder = changeInfo.newHolder;
        final View newView = newHolder != null ? newHolder.itemView : null;
        if (view != null) {
            final ViewPropertyAnimatorCompat oldViewAnim = ViewCompat.animate(view).setDuration(
                    getChangeDuration());
            mChangeAnimations.add(changeInfo.oldHolder);
            oldViewAnim.translationX(changeInfo.toX - changeInfo.fromX);
            oldViewAnim.translationY(changeInfo.toY - changeInfo.fromY);
            oldViewAnim.alpha(0).setListener(new VpaListenerAdapter() {
                @Override
                public void onAnimationStart(View view) {
                    dispatchChangeStarting(changeInfo.oldHolder, true);
                }

                @Override
                public void onAnimationEnd(View view) {
                    oldViewAnim.setListener(null);
                    ViewCompat.setAlpha(view, 1);
                    ViewCompat.setTranslationX(view, 0);
                    ViewCompat.setTranslationY(view, 0);
                    dispatchChangeFinished(changeInfo.oldHolder, true);
                    mChangeAnimations.remove(changeInfo.oldHolder);
                    dispatchFinishedWhenDone();
                }
            }).start();
        }
        if (newView != null) {
            final ViewPropertyAnimatorCompat newViewAnimation = ViewCompat.animate(newView);
            mChangeAnimations.add(changeInfo.newHolder);
            newViewAnimation.translationX(0).translationY(0).setDuration(getChangeDuration()).
                    alpha(1).setListener(new VpaListenerAdapter() {
                @Override
                public void onAnimationStart(View view) {
                    dispatchChangeStarting(changeInfo.newHolder, false);
                }

                @Override
                public void onAnimationEnd(View view) {
                    newViewAnimation.setListener(null);
                    ViewCompat.setAlpha(newView, 1);
                    ViewCompat.setTranslationX(newView, 0);
                    ViewCompat.setTranslationY(newView, 0);
                    dispatchChangeFinished(changeInfo.newHolder, false);
                    mChangeAnimations.remove(changeInfo.newHolder);
                    dispatchFinishedWhenDone();
                }
            }).start();
        }
    }

    /**
     * 改变后动画
     *
     * @param infoList
     * @param item
     */
    private void endChangeAnimation(List<ChangeInfo> infoList, ViewHolder item) {
        for (int i = infoList.size() - 1; i >= 0; i--) {
            ChangeInfo changeInfo = infoList.get(i);
            if (endChangeAnimationIfNecessary(changeInfo, item)) {
                if (changeInfo.oldHolder == null && changeInfo.newHolder == null) {
                    infoList.remove(changeInfo);
                }
            }
        }
    }

    /**
     * @param changeInfo
     */
    private void endChangeAnimationIfNecessary(ChangeInfo changeInfo) {
        if (changeInfo.oldHolder != null) {
            endChangeAnimationIfNecessary(changeInfo, changeInfo.oldHolder);
        }
        if (changeInfo.newHolder != null) {
            endChangeAnimationIfNecessary(changeInfo, changeInfo.newHolder);
        }
    }

    /**
     * @param changeInfo
     * @param item
     * @return
     */
    private boolean endChangeAnimationIfNecessary(ChangeInfo changeInfo, ViewHolder item) {
        boolean oldItem = false;
        if (changeInfo.newHolder == item) {
            changeInfo.newHolder = null;
        } else if (changeInfo.oldHolder == item) {
            changeInfo.oldHolder = null;
            oldItem = true;
        } else {
            return false;
        }
        ViewCompat.setAlpha(item.itemView, 1);
        ViewCompat.setTranslationX(item.itemView, 0);
        ViewCompat.setTranslationY(item.itemView, 0);
        dispatchChangeFinished(item, oldItem);
        return true;
    }

    /**
     * @param item
     */
    @Override
    public void endAnimation(ViewHolder item) {
        final View view = item.itemView;
        // this will trigger end callback which should set properties to their target values.
        ViewCompat.animate(view).cancel();
        // TODO if some other animations are chained to end, how do we cancel them as well?
        for (int i = mPendingMoves.size() - 1; i >= 0; i--) {
            MoveInfo moveInfo = mPendingMoves.get(i);
            if (moveInfo.holder == item) {
                ViewCompat.setTranslationY(view, 0);
                ViewCompat.setTranslationX(view, 0);
                dispatchMoveFinished(item);
                mPendingMoves.remove(i);
            }
        }
        endChangeAnimation(mPendingChanges, item);
        if (mPendingRemovals.remove(item)) {
            ViewCompat.setAlpha(view, 1);
            dispatchRemoveFinished(item);
        }
        if (mPendingAdditions.remove(item)) {
            ViewCompat.setAlpha(view, 1);
            dispatchAddFinished(item);
        }

        for (int i = mChangesList.size() - 1; i >= 0; i--) {
            ArrayList<ChangeInfo> changes = mChangesList.get(i);
            endChangeAnimation(changes, item);
            if (changes.isEmpty()) {
                mChangesList.remove(i);
            }
        }
        for (int i = mMovesList.size() - 1; i >= 0; i--) {
            ArrayList<MoveInfo> moves = mMovesList.get(i);
            for (int j = moves.size() - 1; j >= 0; j--) {
                MoveInfo moveInfo = moves.get(j);
                if (moveInfo.holder == item) {
                    ViewCompat.setTranslationY(view, 0);
                    ViewCompat.setTranslationX(view, 0);
                    dispatchMoveFinished(item);
                    moves.remove(j);
                    if (moves.isEmpty()) {
                        mMovesList.remove(i);
                    }
                    break;
                }
            }
        }
        for (int i = mAdditionsList.size() - 1; i >= 0; i--) {
            ArrayList<ViewHolder> additions = mAdditionsList.get(i);
            if (additions.remove(item)) {
                ViewCompat.setAlpha(view, 1);
                dispatchAddFinished(item);
                if (additions.isEmpty()) {
                    mAdditionsList.remove(i);
                }
            }
        }

        // animations should be ended by the cancel above.
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (mRemoveAnimations.remove(item) && DEBUG) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mRemoveAnimations list");
        }

        //noinspection PointlessBooleanExpression,ConstantConditions
        if (mAddAnimations.remove(item) && DEBUG) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mAddAnimations list");
        }

        //noinspection PointlessBooleanExpression,ConstantConditions
        if (mChangeAnimations.remove(item) && DEBUG) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mChangeAnimations list");
        }

        //noinspection PointlessBooleanExpression,ConstantConditions
        if (mMoveAnimations.remove(item) && DEBUG) {
            throw new IllegalStateException("after animation is cancelled, item should not be in "
                    + "mMoveAnimations list");
        }
        dispatchFinishedWhenDone();
    }

    private void resetAnimation(ViewHolder holder) {
        AnimatorCompatHelper.clearInterpolator(holder.itemView);
        endAnimation(holder);
    }

    /**
     * @return
     */
    @Override
    public boolean isRunning() {
        return (!mPendingAdditions.isEmpty() ||
                !mPendingChanges.isEmpty() ||
                !mPendingMoves.isEmpty() ||
                !mPendingRemovals.isEmpty() ||
                !mMoveAnimations.isEmpty() ||
                !mRemoveAnimations.isEmpty() ||
                !mAddAnimations.isEmpty() ||
                !mChangeAnimations.isEmpty() ||
                !mMovesList.isEmpty() ||
                !mAdditionsList.isEmpty() ||
                !mChangesList.isEmpty());
    }

    /**
     * Check the state of currently pending and running animations. If there are none
     * pending/running, call {@link #dispatchAnimationsFinished()} to notify any
     * listeners.
     */
    void dispatchFinishedWhenDone() {
        if (!isRunning()) {
            dispatchAnimationsFinished();
        }
    }

    /**
     *
     */
    @Override
    public void endAnimations() {
        int count = mPendingMoves.size();
        for (int i = count - 1; i >= 0; i--) {
            MoveInfo item = mPendingMoves.get(i);
            View view = item.holder.itemView;
            ViewCompat.setTranslationY(view, 0);
            ViewCompat.setTranslationX(view, 0);
            dispatchMoveFinished(item.holder);
            mPendingMoves.remove(i);
        }
        count = mPendingRemovals.size();
        for (int i = count - 1; i >= 0; i--) {
            ViewHolder item = mPendingRemovals.get(i);
            dispatchRemoveFinished(item);
            mPendingRemovals.remove(i);
        }
        count = mPendingAdditions.size();
        for (int i = count - 1; i >= 0; i--) {
            ViewHolder item = mPendingAdditions.get(i);
            View view = item.itemView;
            ViewCompat.setAlpha(view, 1);
            dispatchAddFinished(item);
            mPendingAdditions.remove(i);
        }
        count = mPendingChanges.size();
        for (int i = count - 1; i >= 0; i--) {
            endChangeAnimationIfNecessary(mPendingChanges.get(i));
        }
        mPendingChanges.clear();
        if (!isRunning()) {
            return;
        }

        int listCount = mMovesList.size();
        for (int i = listCount - 1; i >= 0; i--) {
            ArrayList<MoveInfo> moves = mMovesList.get(i);
            count = moves.size();
            for (int j = count - 1; j >= 0; j--) {
                MoveInfo moveInfo = moves.get(j);
                ViewHolder item = moveInfo.holder;
                View view = item.itemView;
                ViewCompat.setTranslationY(view, 0);
                ViewCompat.setTranslationX(view, 0);
                dispatchMoveFinished(moveInfo.holder);
                moves.remove(j);
                if (moves.isEmpty()) {
                    mMovesList.remove(moves);
                }
            }
        }
        listCount = mAdditionsList.size();
        for (int i = listCount - 1; i >= 0; i--) {
            ArrayList<ViewHolder> additions = mAdditionsList.get(i);
            count = additions.size();
            for (int j = count - 1; j >= 0; j--) {
                ViewHolder item = additions.get(j);
                View view = item.itemView;
                ViewCompat.setAlpha(view, 1);
                dispatchAddFinished(item);
                additions.remove(j);
                if (additions.isEmpty()) {
                    mAdditionsList.remove(additions);
                }
            }
        }
        listCount = mChangesList.size();
        for (int i = listCount - 1; i >= 0; i--) {
            ArrayList<ChangeInfo> changes = mChangesList.get(i);
            count = changes.size();
            for (int j = count - 1; j >= 0; j--) {
                endChangeAnimationIfNecessary(changes.get(j));
                if (changes.isEmpty()) {
                    mChangesList.remove(changes);
                }
            }
        }

        cancelAll(mRemoveAnimations);
        cancelAll(mMoveAnimations);
        cancelAll(mAddAnimations);
        cancelAll(mChangeAnimations);

        dispatchAnimationsFinished();
    }

    /**
     * @param viewHolders
     */
    void cancelAll(List<ViewHolder> viewHolders) {
        for (int i = viewHolders.size() - 1; i >= 0; i--) {
            ViewCompat.animate(viewHolders.get(i).itemView).cancel();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the payload list is not empty, DefaultItemAnimator returns <code>true</code>.
     * When this is the case:
     * <ul>
     * <li>If you override {@link #animateChange(ViewHolder, ViewHolder, int, int, int, int)}, both
     * ViewHolder arguments will be the same instance.
     * </li>
     * <li>
     * If you are not overriding {@link #animateChange(ViewHolder, ViewHolder, int, int, int, int)},
     * then DefaultItemAnimator will call {@link #animateMove(ViewHolder, int, int, int, int)} and
     * run a move animation instead.
     * </li>
     * </ul>
     */
    @Override
    public boolean canReuseUpdatedViewHolder(@NonNull ViewHolder viewHolder, @NonNull List<Object> payloads) {
        return !payloads.isEmpty() || super.canReuseUpdatedViewHolder(viewHolder, payloads);
    }

    /**
     *
     */
    private static class VpaListenerAdapter implements ViewPropertyAnimatorListener {

        VpaListenerAdapter() {
        }

        @Override
        public void onAnimationStart(View view) {
        }

        @Override
        public void onAnimationEnd(View view) {
        }

        @Override
        public void onAnimationCancel(View view) {
        }
    }
}
