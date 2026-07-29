/**
 * Touch Drag-and-Drop for mobile devices.
 *
 * Activates only on touch-only devices (no mouse pointer).
 *
 * Supported gestures (hold ~400ms, then drag):
 *  - Main task item  → reorder within column, move between columns, or drop onto
 *                      another task to convert to a subtask
 *  - Subtask item    → reorder within the subtask panel, OR drag outside the panel
 *                      to promote to a main task in any column
 *
 * Short touches and scrolls are never interrupted.
 */
(function () {
    'use strict';

    if (!('ontouchstart' in window)) return;

    const HOLD_DELAY = 380;    // ms before drag is initiated
    const MOVE_THRESHOLD = 10; // px — cancels hold if exceeded before delay fires

    // ─── Drag state ──────────────────────────────────────────

    let holdTimer = null;
    let isDragging = false;
    let isSubtaskDrag = false; // true when dragging from the subtask panel

    let dragElement = null;   // the real element (dimmed, stays in DOM)
    let ghostElement = null;  // fixed-position visual clone
    let startX = 0;
    let startY = 0;
    let offsetX = 0;
    let offsetY = 0;
    let sourceColumn = null;  // set for main-task drags
    let parentTaskId = null;  // set for subtask drags

    // ─── DOM helpers ─────────────────────────────────────────

    function getMainTaskItem(el) {
        const item = el && el.closest('.task-item');
        if (!item) return null;
        if (item.closest('.subtask-list')) return null; // not a main-column item
        return item;
    }

    function getSubtaskItem(el) {
        const item = el && el.closest('.task-item');
        if (!item) return null;
        if (!item.closest('.subtask-list')) return null; // not a subtask
        return item;
    }

    function getColumnId(el) {
        const col = el && el.closest('.task-column');
        return col ? col.id : null;
    }

    function getTaskList(el) {
        const col = el && el.closest('.task-column');
        return col ? col.querySelector('.task-list') : null;
    }

    function getDragAfterElement(container, y) {
        const items = [...container.querySelectorAll('.task-item:not(.dragging)')];
        return items.reduce((closest, child) => {
            const box = child.getBoundingClientRect();
            const offset = y - box.top - box.height / 2;
            if (offset < 0 && offset > closest.offset) return { offset, element: child };
            return closest;
        }, { offset: Number.NEGATIVE_INFINITY }).element;
    }

    function clearHighlights() {
        document.querySelectorAll('.task-item').forEach(el => el.style.removeProperty('box-shadow'));
    }

    // ─── Ghost ───────────────────────────────────────────────

    function createGhost(el, x, y) {
        const rect = el.getBoundingClientRect();
        offsetX = x - rect.left;
        offsetY = y - rect.top;

        const ghost = el.cloneNode(true);
        ghost.id = '';
        ghost.className = el.className + ' touch-drag-ghost';
        ghost.style.position = 'fixed';
        ghost.style.width = rect.width + 'px';
        ghost.style.left = (x - offsetX) + 'px';
        ghost.style.top  = (y - offsetY) + 'px';
        ghost.style.zIndex = '99999';
        ghost.style.pointerEvents = 'none';
        ghost.style.opacity = '0.88';
        ghost.style.transform = 'scale(1.03)';
        ghost.style.boxShadow = '0 8px 24px rgba(0,0,0,0.45)';
        ghost.style.transition = 'none';
        document.body.appendChild(ghost);
        return ghost;
    }

    function moveGhost(x, y) {
        if (!ghostElement) return;
        ghostElement.style.left = (x - offsetX) + 'px';
        ghostElement.style.top  = (y - offsetY) + 'px';
    }

    function removeGhost() {
        if (ghostElement) { ghostElement.remove(); ghostElement = null; }
    }

    function underFinger(x, y) {
        ghostElement.style.visibility = 'hidden';
        const el = document.elementFromPoint(x, y);
        ghostElement.style.visibility = '';
        return el;
    }

    // ─── Start ───────────────────────────────────────────────

    function startDrag(el, x, y, isSubtask) {
        isDragging = true;
        isSubtaskDrag = isSubtask;
        dragElement = el;

        if (isSubtask) {
            // Make panel pass-through so we can detect columns underneath
            const panel = document.getElementById('task-panel');
            if (panel) panel.classList.add('dragging-subtask');
        } else {
            sourceColumn = getColumnId(el);
        }

        el.classList.add('dragging');
        el.style.opacity = '0.25';

        ghostElement = createGhost(el, x, y);

        if (navigator.vibrate) navigator.vibrate(30);

        // Scroll is blocked via e.preventDefault() in touchmove (passive:false).
        // body overflow:hidden is intentionally omitted — it does nothing on iOS Safari.
    }

    // ─── Move ────────────────────────────────────────────────

    function handleDragMove(x, y) {
        moveGhost(x, y);

        const below = underFinger(x, y);
        clearHighlights();

        if (!below) return;

        if (isSubtaskDrag) {
            handleSubtaskMove(below, y);
        } else {
            handleMainTaskMove(below, y);
        }
    }

    function handleMainTaskMove(below, y) {
        // Hovering over a different main task → subtask-conversion highlight
        const taskBelow = getMainTaskItem(below);
        if (taskBelow && taskBelow !== dragElement) {
            taskBelow.style.boxShadow = '0 0 0 2px #ff6b2b';
            return;
        }

        // Hovering over a column list → live-preview reorder
        const listBelow = getTaskList(below);
        if (listBelow) {
            const after = getDragAfterElement(listBelow, y);
            if (after) listBelow.insertBefore(dragElement, after);
            else        listBelow.appendChild(dragElement);
        }
    }

    function handleSubtaskMove(below, y) {
        // Still inside the subtask-list → reorder preview
        const subtaskList = below.closest('.subtask-list');
        if (subtaskList) {
            const after = getDragAfterElement(subtaskList, y);
            if (after) subtaskList.insertBefore(dragElement, after);
            else        subtaskList.appendChild(dragElement);
            return;
        }

        // Over a main-column list → promote-to-task preview highlight
        const listBelow = getTaskList(below);
        if (listBelow) {
            listBelow.closest('.task-column').style.outline = '2px solid #ff6b2b';
        }
    }

    // ─── End / Drop ──────────────────────────────────────────

    function endDrag(x, y) {
        if (!isDragging || !dragElement) return;

        const below = underFinger(x, y);
        const tm = window.taskManager;

        if (isSubtaskDrag) {
            dropSubtask(below, x, y, tm);
        } else {
            dropMainTask(below, x, y, tm);
        }

        // Restore
        if (document.body.contains(dragElement)) {
            dragElement.classList.remove('dragging');
            dragElement.style.removeProperty('opacity');
        }

        const panel = document.getElementById('task-panel');
        if (panel) panel.classList.remove('dragging-subtask');

        // Clear column outlines (used by subtask promote preview)
        document.querySelectorAll('.task-column').forEach(c => c.style.removeProperty('outline'));

        clearHighlights();
        removeGhost();


        isDragging = false;
        isSubtaskDrag = false;
        dragElement = null;
        sourceColumn = null;
        parentTaskId = null;
    }

    function dropMainTask(below, x, y, tm) {
        const taskBelow = below ? getMainTaskItem(below) : null;
        const toColumnId = getColumnId(dragElement);

        if (taskBelow && taskBelow !== dragElement) {
            // Drop on task → convert to subtask
            if (tm) tm.moveTaskToSubtask(dragElement.dataset.taskId, sourceColumn, taskBelow.dataset.taskId);
        } else if (toColumnId && tm) {
            if (sourceColumn !== toColumnId) {
                // Cross-column: update data model to match DOM
                const task = tm.lists[sourceColumn].getTask(dragElement.dataset.taskId);
                if (task) {
                    tm.lists[sourceColumn].removeTask(task.id);
                    tm.lists[toColumnId].addTask(task);
                    dragElement.dataset.sourceColumn = toColumnId;
                }
            }
            tm.updateTaskOrder(toColumnId);
        }
    }

    function dropSubtask(below, x, y, tm) {
        if (!tm || !tm.currentlyEditingTask) return;

        const subtaskList = document.querySelector('.subtask-list');

        // Finger is inside the subtask panel → reorder
        if (below && below.closest('.subtask-list')) {
            const newOrder = [];
            subtaskList.querySelectorAll('.task-item').forEach(el => {
                const subtask = tm.currentlyEditingTask.subtasks.find(s => s.id === el.dataset.subtaskId);
                if (subtask) newOrder.push(subtask);
            });
            tm.currentlyEditingTask.subtasks = newOrder;
            tm.saveToDb();
            return;
        }

        // Finger is outside the panel → promote to main task
        const toColumnId = below ? getColumnId(below) : null;
        if (toColumnId) {
            const listEl = document.querySelector(`#${toColumnId} .task-list`);
            const afterEl = listEl ? getDragAfterElement(listEl, y) : null;
            tm.moveSubtaskToMainList(
                dragElement.dataset.subtaskId,
                tm.currentlyEditingTask.id,
                toColumnId,
                afterEl
            );
        }
    }

    // ─── Cancel ──────────────────────────────────────────────

    function cancelDrag() {
        if (holdTimer !== null) { clearTimeout(holdTimer); holdTimer = null; }
        if (!isDragging) return;

        if (document.body.contains(dragElement)) {
            dragElement.classList.remove('dragging');
            dragElement.style.removeProperty('opacity');
        }

        const panel = document.getElementById('task-panel');
        if (panel) panel.classList.remove('dragging-subtask');

        document.querySelectorAll('.task-column').forEach(c => c.style.removeProperty('outline'));
        clearHighlights();
        removeGhost();


        isDragging = false;
        isSubtaskDrag = false;
        dragElement = null;
        sourceColumn = null;
        parentTaskId = null;
    }

    // ─── Touch event listeners ────────────────────────────────

    document.addEventListener('touchstart', function (e) {
        if (isDragging) return;

        // Let interactive elements work normally
        if (e.target.closest('button, a, input, .tag-pill, .tag-remove, .task-checkbox')) return;

        const subtaskItem = getSubtaskItem(e.target);
        const mainTaskItem = subtaskItem ? null : getMainTaskItem(e.target);

        const el = subtaskItem || mainTaskItem;
        if (!el) return;

        const touch = e.touches[0];
        startX = touch.clientX;
        startY = touch.clientY;

        holdTimer = setTimeout(function () {
            holdTimer = null;
            startDrag(el, startX, startY, !!subtaskItem);
        }, HOLD_DELAY);
    }, { passive: true });

    document.addEventListener('touchmove', function (e) {
        const touch = e.touches[0];
        const x = touch.clientX;
        const y = touch.clientY;

        if (isDragging) {
            e.preventDefault();
            handleDragMove(x, y);
            return;
        }

        if (holdTimer !== null) {
            if (Math.abs(x - startX) > MOVE_THRESHOLD || Math.abs(y - startY) > MOVE_THRESHOLD) {
                clearTimeout(holdTimer);
                holdTimer = null;
            }
        }
    }, { passive: false });

    document.addEventListener('touchend', function (e) {
        if (holdTimer !== null) { clearTimeout(holdTimer); holdTimer = null; }

        if (isDragging) {
            const touch = e.changedTouches[0];
            endDrag(touch.clientX, touch.clientY);
            e.preventDefault();
        }
    }, { passive: false });

    document.addEventListener('touchcancel', cancelDrag, { passive: true });

})();
