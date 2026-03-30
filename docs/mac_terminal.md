See all sessions : tmux ls
Stop session : tmux kill-session -t dev

# daily use : 
tmux new -s dev
tmux new -s hybris
tmux new -s nodeapp
tmux new -s kafka-learing

Vertical split:
tmux split-window -h
Horizontal split:
tmux split-window -v

Apply config
tmux source-file ~/.tmux.conf
nano ~/.tmux.conf