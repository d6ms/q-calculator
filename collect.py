import os
import json

target_dir = 'java-large+'

data = dict()
for dataset in os.listdir(target_dir):
    data[dataset] = dict()
    for project_txt in os.listdir(target_dir + '/' + dataset):
        with open(target_dir + '/' + dataset + '/' + project_txt, mode='r') as f:
            q_value = f.readline().rstrip()
        project = project_txt.replace('.txt', '')
        data[dataset][project] = float(q_value)

with open('q_values.json', mode='w') as f:
    json.dump(data, f, indent=2);
