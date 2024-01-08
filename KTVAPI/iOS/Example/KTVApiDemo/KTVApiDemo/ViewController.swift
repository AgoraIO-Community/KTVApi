//
//  ViewController.swift
//  KTVApiDemo
//
//  Created by CP on 2023/8/8.
//

import UIKit

class ViewController: UIViewController {
    var role: KTVSingRole = .audience
    var channelName: String = ""
    var type: LoadMusicType = .mcc
    var rtcToken: String?
    var rtmToken: String?
    var rtcPlayerToken: String?
    var userId: Int = 0
    @IBOutlet weak var tf: UITextField!
    override func viewDidLoad() {
        super.viewDidLoad()

    }

    
    @IBAction func leadSet(_ sender: UIButton) {
        role = .leadSinger
    }
    
    @IBAction func auSet(_ sender: Any) {
        role = .audience
    }
    
    @IBAction func valueChange(_ sender: UISegmentedControl) {
        type = sender.selectedSegmentIndex == 0 ? .mcc : .local
    }
    
    @IBAction func startSing(_ sender: UIButton) {
        if tf.text?.count == 0 {
            return
        }
        
        channelName = tf.text!
        let vc = KTVViewController()
        vc.role = role
        vc.type = type
        vc.channelName = channelName
        self.navigationController?.pushViewController(vc, animated: true)
    }
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        tf.resignFirstResponder()
    }
    
}

